package com.wallettransfer.transfers.model;

import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.transfers.exception.InvalidTransferStateException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String reference;

    @Column(name = "sender_wallet_id", nullable = false)
    private UUID senderWalletId;

    @Column(name = "receiver_wallet_id", nullable = false)
    private UUID receiverWalletId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private TransferFailureReason failureReason;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "idempotency_record_id")
    private UUID idempotencyRecordId;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Transfer() {}

    public Transfer(
            UUID id,
            String reference,
            UUID sender,
            UUID receiver,
            BigDecimal amount,
            Currency currency,
            String description,
            Instant now) {
        this(id, reference, sender, receiver, amount, currency, description, null, null, now);
    }

    public Transfer(
            UUID id,
            String reference,
            UUID sender,
            UUID receiver,
            BigDecimal amount,
            Currency currency,
            String description,
            String idempotencyKey,
            UUID idempotencyRecordId,
            Instant now) {
        this.id = id;
        this.reference = reference;
        this.senderWalletId = sender;
        this.receiverWalletId = receiver;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.idempotencyRecordId = idempotencyRecordId;
        this.status = TransferStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void start(Instant now) {
        if (status != TransferStatus.PENDING) {
            throw new InvalidTransferStateException();
        }
        status = TransferStatus.PROCESSING;
        updatedAt = now;
    }

    public void succeed(Instant now) {
        if (status != TransferStatus.PROCESSING) {
            throw new InvalidTransferStateException();
        }
        status = TransferStatus.SUCCESSFUL;
        updatedAt = now;
        completedAt = now;
    }

    public void reverse(Instant now) {
        if (status != TransferStatus.SUCCESSFUL) {
            throw new InvalidTransferStateException();
        }
        status = TransferStatus.REVERSED;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public UUID getSenderWalletId() {
        return senderWalletId;
    }

    public UUID getReceiverWalletId() {
        return receiverWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public TransferFailureReason getFailureReason() {
        return failureReason;
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
