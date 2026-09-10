package com.wallettransfer.reconciliation.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_reconciliation_attempts")
public class ProviderReconciliationAttempt {
    @Id
    private UUID id;

    @Column(name = "external_transfer_id")
    private UUID transferId;

    private String provider;

    @Column(name = "attempt_number")
    private int attemptNumber;

    @Column(name = "query_reference")
    private String queryReference;

    private String outcome;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "error_category")
    private String errorCategory;

    @Column(name = "error_message_masked")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ProviderReconciliationAttempt() {}

    public ProviderReconciliationAttempt(
            UUID id,
            UUID transferId,
            int attempt,
            String reference,
            String outcome,
            String category,
            String error,
            Instant start,
            Instant end) {
        this.id = id;
        this.transferId = transferId;
        provider = "SIMULATOR";
        attemptNumber = attempt;
        queryReference = reference;
        this.outcome = outcome;
        errorCategory = category;
        errorMessage = error == null ? null : error.substring(0, Math.min(500, error.length()));
        startedAt = start;
        completedAt = end;
    }
}
