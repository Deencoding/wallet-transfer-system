package com.wallettransfer.wallets.model;

import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.wallets.exception.InvalidWalletStateTransitionException;
import com.wallettransfer.wallets.exception.WalletHasBalanceException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletStatus status;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "ledger_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal ledgerBalance;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {}

    public Wallet(UUID id, UUID ownerId, Instant now) {
        this.id = id;
        this.ownerId = ownerId;
        this.currency = Currency.NGN;
        this.status = WalletStatus.ACTIVE;
        this.availableBalance = ZERO;
        this.ledgerBalance = ZERO;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getLedgerBalance() {
        return ledgerBalance;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void changeStatus(WalletStatus target, Instant now) {
        if (target == status || status == WalletStatus.CLOSED)
            throw new InvalidWalletStateTransitionException(status, target);
        boolean allowed = (status == WalletStatus.ACTIVE
                        && (target == WalletStatus.FROZEN || target == WalletStatus.CLOSED))
                || (status == WalletStatus.FROZEN && (target == WalletStatus.ACTIVE || target == WalletStatus.CLOSED));
        if (!allowed) {
            throw new InvalidWalletStateTransitionException(status, target);
        }
        if (target == WalletStatus.CLOSED && (availableBalance.signum() != 0 || ledgerBalance.signum() != 0))
            throw new WalletHasBalanceException();
        status = target;
        updatedAt = now;
    }

    public void debit(BigDecimal amount, Instant now) {
        if (status != WalletStatus.ACTIVE)
            throw new com.wallettransfer.wallets.exception.WalletTransferRejectedException(
                    com.wallettransfer.shared.exception.DomainErrorCode.SENDER_WALLET_UNAVAILABLE,
                    "Sender wallet cannot initiate transfers");
        if (availableBalance.compareTo(amount) < 0)
            throw new com.wallettransfer.wallets.exception.WalletTransferRejectedException(
                    com.wallettransfer.shared.exception.DomainErrorCode.INSUFFICIENT_FUNDS,
                    "Insufficient available funds");
        availableBalance = availableBalance.subtract(amount);
        ledgerBalance = ledgerBalance.subtract(amount);
        updatedAt = now;
    }

    public void credit(BigDecimal amount, Instant now) {
        if (status == WalletStatus.CLOSED)
            throw new com.wallettransfer.wallets.exception.WalletTransferRejectedException(
                    com.wallettransfer.shared.exception.DomainErrorCode.RECEIVER_WALLET_UNAVAILABLE,
                    "Receiver wallet cannot receive transfers");
        availableBalance = availableBalance.add(amount);
        ledgerBalance = ledgerBalance.add(amount);
        updatedAt = now;
    }

    public void reserve(BigDecimal amount, Instant now) {
        if (status != WalletStatus.ACTIVE)
            throw new com.wallettransfer.wallets.exception.WalletTransferRejectedException(
                    com.wallettransfer.shared.exception.DomainErrorCode.SENDER_WALLET_UNAVAILABLE,
                    "Sender wallet cannot initiate transfers");
        if (availableBalance.compareTo(amount) < 0)
            throw new com.wallettransfer.wallets.exception.WalletTransferRejectedException(
                    com.wallettransfer.shared.exception.DomainErrorCode.INSUFFICIENT_FUNDS,
                    "Insufficient available funds");
        availableBalance = availableBalance.subtract(amount);
        updatedAt = now;
    }

    public void releaseReservation(BigDecimal amount, Instant now) {
        availableBalance = availableBalance.add(amount);
        updatedAt = now;
    }

    public void settleReservation(BigDecimal amount, Instant now) {
        if (ledgerBalance.compareTo(amount) < 0)
            throw new IllegalStateException("Ledger balance cannot settle reservation");
        ledgerBalance = ledgerBalance.subtract(amount);
        updatedAt = now;
    }
}
