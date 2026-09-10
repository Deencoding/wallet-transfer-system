package com.wallettransfer.externaltransfers.model;

import com.wallettransfer.shared.money.Currency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_transfers")
public class ExternalTransfer {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "sender_wallet_id")
    private UUID senderWalletId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    @Column(name = "beneficiary_token")
    private String beneficiaryToken;

    private String provider;

    @Column(name = "provider_request_reference")
    private String providerRequestReference;

    @Column(name = "provider_transfer_reference")
    private String providerTransferReference;

    @Enumerated(EnumType.STRING)
    private ExternalTransferStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    private String description;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_fingerprint")
    private String requestFingerprint;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "uncertain_since")
    private Instant uncertainSince;

    @Column(name = "next_reconciliation_at")
    private Instant nextReconciliationAt;

    @Column(name = "reconciliation_attempts")
    private int reconciliationAttempts;

    @Column(name = "last_status_check_at")
    private Instant lastStatusCheckAt;

    @Column(name = "last_provider_error")
    private String lastProviderError;

    @Column(name = "reconciliation_claimed_at")
    private Instant reconciliationClaimedAt;

    @Version
    private long version;

    protected ExternalTransfer() {}

    public ExternalTransfer(
            UUID id,
            String reference,
            UUID ownerId,
            UUID walletId,
            BigDecimal amount,
            Currency currency,
            String token,
            String requestRef,
            String description,
            String key,
            String fingerprint,
            Instant now) {
        this.id = id;
        this.reference = reference;
        this.ownerId = ownerId;
        senderWalletId = walletId;
        this.amount = amount;
        this.currency = currency;
        beneficiaryToken = token;
        provider = "SIMULATOR";
        providerRequestReference = requestRef;
        this.description = description;
        idempotencyKey = key;
        requestFingerprint = fingerprint;
        status = ExternalTransferStatus.PENDING;
        createdAt = now;
        updatedAt = now;
    }

    public void processing(Instant now) {
        if (status != ExternalTransferStatus.PENDING)
            throw new IllegalStateException("External transfer is not pending");
        status = ExternalTransferStatus.PROCESSING;
        updatedAt = now;
    }

    public void uncertain(Instant now, String error) {
        if (status != ExternalTransferStatus.PROCESSING
                && status != ExternalTransferStatus.PENDING_PROVIDER_CONFIRMATION)
            throw new IllegalStateException("External transfer cannot become uncertain");
        status = ExternalTransferStatus.PENDING_PROVIDER_CONFIRMATION;
        if (uncertainSince == null) {
            uncertainSince = now;
        }
        nextReconciliationAt = now.plusSeconds(10);
        lastProviderError = mask(error);
        reconciliationClaimedAt = null;
        updatedAt = now;
    }

    public void claimReconciliation(Instant now) {
        reconciliationClaimedAt = now;
        nextReconciliationAt = null;
    }

    public int recordStatusCheck(Instant now, String outcome, int maxAttempts) {
        reconciliationAttempts++;
        lastStatusCheckAt = now;
        reconciliationClaimedAt = null;
        if (reconciliationAttempts >= maxAttempts && "UNKNOWN".equals(outcome)) {
            status = ExternalTransferStatus.MANUAL_REVIEW;
            nextReconciliationAt = null;
        } else if ("PENDING".equals(outcome) || "UNKNOWN".equals(outcome)) {
            nextReconciliationAt = now.plusSeconds(Math.min(900, 10L * (1L << Math.min(reconciliationAttempts, 6))));
        }
        updatedAt = now;
        return reconciliationAttempts;
    }

    public void succeed(String providerReference, Instant now) {
        if (status != ExternalTransferStatus.PROCESSING
                && status != ExternalTransferStatus.PENDING_PROVIDER_CONFIRMATION
                && status != ExternalTransferStatus.MANUAL_REVIEW)
            throw new IllegalStateException("External transfer cannot succeed");
        providerTransferReference = providerReference;
        status = ExternalTransferStatus.SUCCESSFUL;
        completedAt = now;
        nextReconciliationAt = null;
        reconciliationClaimedAt = null;
        updatedAt = now;
    }

    public void fail(String reason, Instant now) {
        if (status != ExternalTransferStatus.PROCESSING
                && status != ExternalTransferStatus.PENDING_PROVIDER_CONFIRMATION
                && status != ExternalTransferStatus.MANUAL_REVIEW)
            throw new IllegalStateException("External transfer cannot fail");
        failureReason = reason;
        status = ExternalTransferStatus.FAILED;
        completedAt = now;
        nextReconciliationAt = null;
        reconciliationClaimedAt = null;
        updatedAt = now;
    }

    private String mask(String value) {
        return value == null ? null : value.substring(0, Math.min(500, value.length()));
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getSenderWalletId() {
        return senderWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getBeneficiaryToken() {
        return beneficiaryToken;
    }

    public String getProviderRequestReference() {
        return providerRequestReference;
    }

    public String getProviderTransferReference() {
        return providerTransferReference;
    }

    public ExternalTransferStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getDescription() {
        return description;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public int getReconciliationAttempts() {
        return reconciliationAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
