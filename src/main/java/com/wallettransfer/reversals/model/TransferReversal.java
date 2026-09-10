package com.wallettransfer.reversals.model;

import com.wallettransfer.shared.money.Currency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_reversals")
public class TransferReversal {
    @Id
    private UUID id;

    private String reference;

    @Column(name = "original_transfer_id")
    private UUID originalTransferId;

    @Column(name = "original_transfer_reference")
    private String originalTransferReference;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    private String reason;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_fingerprint")
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    private ReversalStatus status;

    @Column(name = "reversal_journal_id")
    private UUID reversalJournalId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TransferReversal() {}

    public TransferReversal(
            UUID id,
            String reference,
            UUID transferId,
            String transferReference,
            BigDecimal amount,
            Currency currency,
            String reason,
            UUID requestedBy,
            String key,
            String fingerprint,
            Instant now) {
        this.id = id;
        this.reference = reference;
        originalTransferId = transferId;
        originalTransferReference = transferReference;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.requestedBy = requestedBy;
        idempotencyKey = key;
        requestFingerprint = fingerprint;
        status = ReversalStatus.PENDING;
        createdAt = now;
        updatedAt = now;
    }

    public void start(Instant now) {
        if (status != ReversalStatus.PENDING) {
            throw new IllegalStateException("Reversal is not pending");
        }
        status = ReversalStatus.PROCESSING;
        updatedAt = now;
    }

    public void succeed(UUID journalId, Instant now) {
        if (status != ReversalStatus.PROCESSING) {
            throw new IllegalStateException("Reversal is not processing");
        }
        reversalJournalId = journalId;
        status = ReversalStatus.SUCCESSFUL;
        completedAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getOriginalTransferReference() {
        return originalTransferReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getReason() {
        return reason;
    }

    public ReversalStatus getStatus() {
        return status;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
