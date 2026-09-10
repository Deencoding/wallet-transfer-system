package com.wallettransfer.reconciliation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.reconciliation.dto.ReconciliationRunResponse;
import com.wallettransfer.reconciliation.exception.ReconciliationAlreadyRunningException;
import com.wallettransfer.reconciliation.model.ReconciliationRun;
import com.wallettransfer.reconciliation.repository.ReconciliationRunRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialReconciliationService {
    private static final long RECONCILIATION_LOCK_ID = 73190421L;

    private static final String WALLET_RECONCILIATION_SQL =
            """
            SELECT
                w.id,
                w.available_balance,
                w.ledger_balance,
                COALESCE(
                    SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE -e.amount END),
                    0
                ) AS calculated_balance,
                COALESCE((
                    SELECT SUM(r.amount)
                    FROM external_transfer_reservations r
                    WHERE r.wallet_id = w.id AND r.status = 'ACTIVE'
                ), 0) AS reserved_balance
            FROM wallets w
            JOIN ledger_accounts a ON a.wallet_id = w.id
            LEFT JOIN journal_entries e ON e.ledger_account_id = a.id
            GROUP BY w.id
            """;

    private static final String TRANSFER_RECONCILIATION_SQL =
            """
            SELECT
                t.id,
                t.status,
                (SELECT COUNT(*)
                 FROM journal_transactions j
                 WHERE j.source_type = 'TRANSFER' AND j.source_reference = t.reference
                ) AS transfer_journals,
                (SELECT COUNT(*)
                 FROM transfer_reversals r
                 JOIN journal_transactions j ON j.id = r.reversal_journal_id
                 WHERE r.original_transfer_id = t.id AND r.status = 'SUCCESSFUL'
                ) AS reversal_journals
            FROM transfers t
            """;

    private static final String EXTERNAL_TRANSFER_RECONCILIATION_SQL =
            """
            SELECT
                e.id,
                e.status,
                r.status AS reservation_status,
                (SELECT COUNT(*)
                 FROM journal_transactions j
                 WHERE j.source_type = 'EXTERNAL_TRANSFER' AND j.source_reference = e.reference
                ) AS journals
            FROM external_transfers e
            JOIN external_transfer_reservations r ON r.external_transfer_id = e.id
            """;

    private static final String UPSERT_CASE_SQL =
            """
            INSERT INTO reconciliation_cases(
                id, case_key, last_run_id, category, severity, resource_type, resource_id,
                expected_value, actual_value, status, first_detected_at, last_detected_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, 'OPEN', ?, ?)
            ON CONFLICT(case_key) DO UPDATE SET
                last_run_id = EXCLUDED.last_run_id,
                severity = EXCLUDED.severity,
                expected_value = EXCLUDED.expected_value,
                actual_value = EXCLUDED.actual_value,
                last_detected_at = EXCLUDED.last_detected_at,
                status = CASE
                    WHEN reconciliation_cases.status IN ('RESOLVED', 'IGNORED') THEN 'OPEN'
                    ELSE reconciliation_cases.status
                END,
                resolved_at = NULL
            """;

    private static final String RESOLVE_STALE_CASES_SQL =
            """
            UPDATE reconciliation_cases
            SET
                status = 'RESOLVED',
                resolved_at = ?,
                resolution_type = 'AUTO_VERIFIED',
                version = version + 1
            WHERE status IN ('OPEN', 'ACKNOWLEDGED') AND last_run_id <> ?
            """;

    private final JdbcTemplate jdbc;
    private final ReconciliationRunRepository runs;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final MeterRegistry metrics;

    public FinancialReconciliationService(
            JdbcTemplate jdbc,
            ReconciliationRunRepository runs,
            ObjectMapper mapper,
            Clock clock,
            MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.runs = runs;
        this.mapper = mapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public ReconciliationRunResponse run(UUID actorId) {
        acquireReconciliationLock();

        Instant startedAt = clock.instant();
        ReconciliationRun run = new ReconciliationRun(UUID.randomUUID(), actorId, startedAt);
        runs.saveAndFlush(run);

        ReconciliationSummary summary = reconcileWallets(run.getId(), startedAt)
                .add(reconcileTransfers(run.getId(), startedAt))
                .add(reconcileExternalTransfers(run.getId(), startedAt));

        resolveCasesAbsentFrom(run.getId(), startedAt);
        run.complete(summary.scanned(), summary.discrepancies(), clock.instant());
        recordMetrics(summary);
        return ReconciliationRunResponse.from(run);
    }

    private void acquireReconciliationLock() {
        Boolean lockAcquired =
                jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, RECONCILIATION_LOCK_ID);
        if (!Boolean.TRUE.equals(lockAcquired)) {
            throw new ReconciliationAlreadyRunningException();
        }
    }

    private ReconciliationSummary reconcileWallets(UUID runId, Instant detectedAt) {
        var rows = jdbc.query(
                WALLET_RECONCILIATION_SQL,
                (resultSet, rowNumber) -> new WalletRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getBigDecimal("available_balance"),
                        resultSet.getBigDecimal("ledger_balance"),
                        resultSet.getBigDecimal("calculated_balance"),
                        resultSet.getBigDecimal("reserved_balance")));

        long discrepancies = 0;
        for (WalletRow row : rows) {
            BigDecimal expectedAvailable = row.calculatedBalance().subtract(row.reservedBalance());
            boolean balancesDiffer = row.storedLedgerBalance().compareTo(row.calculatedBalance()) != 0
                    || row.availableBalance().compareTo(expectedAvailable) != 0;
            if (balancesDiffer) {
                upsertCase(
                        runId,
                        "WALLET_PROJECTION:" + row.walletId(),
                        "WALLET_PROJECTION",
                        "HIGH",
                        "WALLET",
                        row.walletId(),
                        Map.of("ledgerBalance", row.calculatedBalance(), "availableBalance", expectedAvailable),
                        Map.of(
                                "ledgerBalance", row.storedLedgerBalance(),
                                "availableBalance", row.availableBalance()),
                        detectedAt);
                discrepancies++;
            }
        }
        return new ReconciliationSummary(rows.size(), discrepancies);
    }

    private ReconciliationSummary reconcileTransfers(UUID runId, Instant detectedAt) {
        var rows = jdbc.query(
                TRANSFER_RECONCILIATION_SQL,
                (resultSet, rowNumber) -> new TransferRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("status"),
                        resultSet.getLong("transfer_journals"),
                        resultSet.getLong("reversal_journals")));

        long discrepancies = 0;
        for (TransferRow row : rows) {
            long expectedTransferJournals = expectsTransferJournal(row.status()) ? 1 : 0;
            long expectedReversalJournals = "REVERSED".equals(row.status()) ? 1 : 0;
            boolean journalsDiffer = row.transferJournals() != expectedTransferJournals
                    || row.reversalJournals() != expectedReversalJournals;
            if (journalsDiffer) {
                upsertCase(
                        runId,
                        "INTERNAL_TRANSFER_LEDGER:" + row.transferId(),
                        "INTERNAL_TRANSFER_LEDGER",
                        "CRITICAL",
                        "TRANSFER",
                        row.transferId(),
                        Map.of(
                                "status", row.status(),
                                "transferJournals", expectedTransferJournals,
                                "reversalJournals", expectedReversalJournals),
                        Map.of(
                                "transferJournals", row.transferJournals(),
                                "reversalJournals", row.reversalJournals()),
                        detectedAt);
                discrepancies++;
            }
        }
        return new ReconciliationSummary(rows.size(), discrepancies);
    }

    private boolean expectsTransferJournal(String status) {
        return "SUCCESSFUL".equals(status) || "REVERSED".equals(status);
    }

    private ReconciliationSummary reconcileExternalTransfers(UUID runId, Instant detectedAt) {
        var rows = jdbc.query(
                EXTERNAL_TRANSFER_RECONCILIATION_SQL,
                (resultSet, rowNumber) -> new ExternalTransferRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("status"),
                        resultSet.getString("reservation_status"),
                        resultSet.getLong("journals")));

        long discrepancies = 0;
        for (ExternalTransferRow row : rows) {
            String expectedReservationStatus = expectedReservationStatus(row.transferStatus());
            long expectedJournals = "SUCCESSFUL".equals(row.transferStatus()) ? 1 : 0;
            boolean stateDiffers =
                    !row.reservationStatus().equals(expectedReservationStatus) || row.journals() != expectedJournals;
            if (stateDiffers) {
                upsertCase(
                        runId,
                        "EXTERNAL_TRANSFER_STATE:" + row.transferId(),
                        "EXTERNAL_TRANSFER_STATE",
                        "CRITICAL",
                        "EXTERNAL_TRANSFER",
                        row.transferId(),
                        Map.of("reservation", expectedReservationStatus, "journals", expectedJournals),
                        Map.of("reservation", row.reservationStatus(), "journals", row.journals()),
                        detectedAt);
                discrepancies++;
            }
        }
        return new ReconciliationSummary(rows.size(), discrepancies);
    }

    private String expectedReservationStatus(String transferStatus) {
        return switch (transferStatus) {
            case "SUCCESSFUL" -> "SETTLED";
            case "FAILED" -> "RELEASED";
            default -> "ACTIVE";
        };
    }

    private void resolveCasesAbsentFrom(UUID runId, Instant resolvedAt) {
        jdbc.update(RESOLVE_STALE_CASES_SQL, Timestamp.from(resolvedAt), runId);
    }

    private void recordMetrics(ReconciliationSummary summary) {
        metrics.counter("reconciliation.runs", "outcome", "COMPLETED").increment();
        metrics.counter("reconciliation.discrepancies").increment(summary.discrepancies());
    }

    private void upsertCase(
            UUID runId,
            String key,
            String category,
            String severity,
            String resourceType,
            UUID resourceId,
            Object expected,
            Object actual,
            Instant detectedAt) {
        try {
            Timestamp timestamp = Timestamp.from(detectedAt);
            jdbc.update(
                    UPSERT_CASE_SQL,
                    UUID.randomUUID(),
                    key,
                    runId,
                    category,
                    severity,
                    resourceType,
                    resourceId,
                    mapper.writeValueAsString(expected),
                    mapper.writeValueAsString(actual),
                    timestamp,
                    timestamp);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not persist reconciliation case", exception);
        }
    }

    private record WalletRow(
            UUID walletId,
            BigDecimal availableBalance,
            BigDecimal storedLedgerBalance,
            BigDecimal calculatedBalance,
            BigDecimal reservedBalance) {}

    private record TransferRow(UUID transferId, String status, long transferJournals, long reversalJournals) {}

    private record ExternalTransferRow(
            UUID transferId, String transferStatus, String reservationStatus, long journals) {}

    private record ReconciliationSummary(long scanned, long discrepancies) {
        private ReconciliationSummary add(ReconciliationSummary other) {
            return new ReconciliationSummary(scanned + other.scanned, discrepancies + other.discrepancies);
        }
    }
}
