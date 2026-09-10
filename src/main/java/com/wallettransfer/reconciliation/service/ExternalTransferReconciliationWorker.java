package com.wallettransfer.reconciliation.service;

import com.wallettransfer.externaltransfers.service.ExternalTransferCompletionService;
import com.wallettransfer.providers.service.ResilientProviderTransferService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "application.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ExternalTransferReconciliationWorker {
    private final ReconciliationClaimService claims;
    private final ResilientProviderTransferService provider;
    private final ExternalTransferCompletionService completion;
    private final MeterRegistry metrics;

    public ExternalTransferReconciliationWorker(
            ReconciliationClaimService claims,
            ResilientProviderTransferService provider,
            ExternalTransferCompletionService completion,
            MeterRegistry metrics) {
        this.claims = claims;
        this.provider = provider;
        this.completion = completion;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${application.reconciliation.poll-interval:PT5S}")
    public void reconcile() {
        for (var claim : claims.claim()) {
            Instant start = Instant.now();
            try {
                var result = provider.query(claim.providerRequestReference());
                if (result.status().equals("SUCCESSFUL")) {
                    completion.successful(claim.transferId(), result.providerReference());
                    claims.record(
                            claim.transferId(),
                            claim.attemptNumber(),
                            claim.providerRequestReference(),
                            "SUCCESSFUL",
                            null,
                            null,
                            start,
                            8);
                } else if (result.status().equals("FAILED")) {
                    completion.failed(claim.transferId(), result.failureReason());
                    claims.record(
                            claim.transferId(),
                            claim.attemptNumber(),
                            claim.providerRequestReference(),
                            "FAILED",
                            null,
                            null,
                            start,
                            8);
                } else
                    claims.record(
                            claim.transferId(),
                            claim.attemptNumber(),
                            claim.providerRequestReference(),
                            "PENDING",
                            null,
                            null,
                            start,
                            8);
                metrics.counter("reconciliation.attempts", "outcome", result.status())
                        .increment();
            } catch (Exception error) {
                claims.record(
                        claim.transferId(),
                        claim.attemptNumber(),
                        claim.providerRequestReference(),
                        "UNKNOWN",
                        error.getClass().getSimpleName(),
                        error.getMessage(),
                        start,
                        8);
                metrics.counter("reconciliation.attempts", "outcome", "UNKNOWN").increment();
            }
        }
    }
}
