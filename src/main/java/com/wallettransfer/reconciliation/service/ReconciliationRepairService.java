package com.wallettransfer.reconciliation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.audit.service.ReconciliationRepairAuditService;
import com.wallettransfer.reconciliation.dto.ReconciliationRepairResponse;
import com.wallettransfer.reconciliation.dto.RepairWalletProjectionRequest;
import com.wallettransfer.reconciliation.exception.ReconciliationCaseNotFoundException;
import com.wallettransfer.reconciliation.exception.ReconciliationRepairConflictException;
import com.wallettransfer.reconciliation.exception.UnsafeRepairException;
import com.wallettransfer.reconciliation.model.ReconciliationRepair;
import com.wallettransfer.reconciliation.repository.ReconciliationCaseRepository;
import com.wallettransfer.reconciliation.repository.ReconciliationRepairRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationRepairService {

    private static final String WALLET_POSITION_SQL = "SELECT w.available_balance, w.ledger_balance, "
            + "COALESCE(SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) calculated, "
            + "COALESCE((SELECT SUM(r.amount) FROM external_transfer_reservations r "
            + "WHERE r.wallet_id = w.id AND r.status = 'ACTIVE'), 0) reserved "
            + "FROM wallets w JOIN ledger_accounts a ON a.wallet_id = w.id "
            + "LEFT JOIN journal_entries e ON e.ledger_account_id = a.id "
            + "WHERE w.id = ? GROUP BY w.id";

    private final ReconciliationCaseRepository cases;
    private final ReconciliationRepairRepository repairs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ReconciliationRepairAuditService audit;
    private final Clock clock;

    public ReconciliationRepairService(
            ReconciliationCaseRepository cases,
            ReconciliationRepairRepository repairs,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            ReconciliationRepairAuditService audit,
            Clock clock) {
        this.cases = cases;
        this.repairs = repairs;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ReconciliationRepairResponse repairWallet(
            UUID actor, UUID caseId, String idempotencyKey, RepairWalletProjectionRequest request) {
        validateIdempotencyKey(idempotencyKey);
        String requestFingerprint = fingerprint(caseId, request.reason());

        var existingRepair = repairs.findByRequestedByAndKey(actor, idempotencyKey);
        if (existingRepair.isPresent()) {
            if (!existingRepair.get().getFingerprint().equals(requestFingerprint)) {
                throw new ReconciliationRepairConflictException();
            }
            return response(existingRepair.get().getId(), caseId);
        }

        var reconciliationCase = cases.findByIdForUpdate(caseId).orElseThrow(ReconciliationCaseNotFoundException::new);
        if (!"WALLET_PROJECTION".equals(reconciliationCase.getCategory())
                || !"OPEN".equals(reconciliationCase.getStatus())) {
            throw new UnsafeRepairException("Only an open wallet projection case can be repaired");
        }

        UUID walletId = reconciliationCase.getResourceId();
        Map<String, Object> position = jdbc.queryForMap(WALLET_POSITION_SQL, walletId);
        BigDecimal calculatedLedger = decimal(position, "calculated");
        BigDecimal activeReservations = decimal(position, "reserved");
        BigDecimal expectedAvailable = calculatedLedger.subtract(activeReservations);
        BigDecimal storedLedger = decimal(position, "ledger_balance");
        BigDecimal storedAvailable = decimal(position, "available_balance");

        if (storedLedger.compareTo(calculatedLedger) == 0 && storedAvailable.compareTo(expectedAvailable) == 0) {
            throw new UnsafeRepairException("Wallet projection is already consistent");
        }

        try {
            String before = mapper.writeValueAsString(Map.of(
                    "ledgerBalance", storedLedger,
                    "availableBalance", storedAvailable));
            String after = mapper.writeValueAsString(Map.of(
                    "ledgerBalance", calculatedLedger,
                    "availableBalance", expectedAvailable));

            jdbc.update(
                    "UPDATE wallets SET ledger_balance = ?, available_balance = ?, "
                            + "version = version + 1, updated_at = ? WHERE id = ?",
                    calculatedLedger,
                    expectedAvailable,
                    java.sql.Timestamp.from(clock.instant()),
                    walletId);

            UUID repairId = UUID.randomUUID();
            repairs.save(new ReconciliationRepair(
                    repairId,
                    caseId,
                    idempotencyKey,
                    actor,
                    requestFingerprint,
                    before,
                    after,
                    request.reason(),
                    clock.instant()));
            jdbc.update(
                    "UPDATE reconciliation_cases SET status = 'RESOLVED', resolved_at = ?, "
                            + "resolution_type = 'WALLET_PROJECTION_REBUILT', resolution_reason = ?, "
                            + "resolved_by = ?, version = version + 1 WHERE id = ?",
                    java.sql.Timestamp.from(clock.instant()),
                    request.reason(),
                    actor,
                    caseId);
            audit.record(
                    repairId,
                    actor,
                    walletId,
                    mapper.writeValueAsString(Map.of(
                            "before", mapper.readTree(before),
                            "after", mapper.readTree(after),
                            "reason", request.reason())));
            return response(repairId, caseId);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Could not repair wallet projection", error);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 255) {
            throw new IllegalArgumentException("Invalid Idempotency-Key");
        }
    }

    private BigDecimal decimal(Map<String, Object> values, String key) {
        return (BigDecimal) values.get(key);
    }

    private ReconciliationRepairResponse response(UUID repairId, UUID caseId) {
        return new ReconciliationRepairResponse(repairId, caseId, "SUCCESSFUL");
    }

    private String fingerprint(UUID caseId, String reason) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((caseId + "|" + reason).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            throw new IllegalStateException("Could not fingerprint reconciliation repair request", error);
        }
    }
}
