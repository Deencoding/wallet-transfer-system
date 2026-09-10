package com.wallettransfer.reconciliation.service;

import com.wallettransfer.externaltransfers.repository.ExternalTransferRepository;
import com.wallettransfer.reconciliation.dto.ReconciliationClaim;
import com.wallettransfer.reconciliation.model.ProviderReconciliationAttempt;
import com.wallettransfer.reconciliation.repository.ProviderReconciliationAttemptRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationClaimService {
    private final ExternalTransferRepository transfers;
    private final ProviderReconciliationAttemptRepository attempts;
    private final Clock clock;

    public ReconciliationClaimService(
            ExternalTransferRepository transfers, ProviderReconciliationAttemptRepository attempts, Clock clock) {
        this.transfers = transfers;
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional
    public List<ReconciliationClaim> claim() {
        Instant now = clock.instant();
        var rows = transfers.findReconciliationCandidates(now, now.minus(Duration.ofMinutes(2)), 25);
        var result = new ArrayList<ReconciliationClaim>();
        for (var transfer : rows) {
            transfer.claimReconciliation(now);
            result.add(new ReconciliationClaim(
                    transfer.getId(),
                    transfer.getProviderRequestReference(),
                    transfer.getReconciliationAttempts() + 1));
        }
        return result;
    }

    @Transactional
    public void record(
            UUID id,
            int attempt,
            String reference,
            String outcome,
            String category,
            String error,
            Instant started,
            int maxAttempts) {
        var transfer = transfers.findByIdForUpdate(id).orElseThrow();
        transfer.recordStatusCheck(clock.instant(), outcome, maxAttempts);
        attempts.save(new ProviderReconciliationAttempt(
                UUID.randomUUID(), id, attempt, reference, outcome, category, error, started, clock.instant()));
    }
}
