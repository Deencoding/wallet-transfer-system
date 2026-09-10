package com.wallettransfer.ledger.model;

import com.wallettransfer.shared.money.Currency;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {
    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "account_code", nullable = false, length = 80)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private LedgerAccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerAccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerAccount() {}

    public LedgerAccount(UUID id, UUID walletId, Currency currency, Instant now) {
        this.id = id;
        this.walletId = walletId;
        this.currency = currency;
        this.createdAt = now;
        this.accountType = LedgerAccountType.LIABILITY;
        this.status = LedgerAccountStatus.ACTIVE;
        this.accountCode = "WALLET-" + currency + "-" + walletId.toString().replace("-", "");
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LedgerAccountStatus getStatus() {
        return status;
    }

    public String getAccountCode() {
        return accountCode;
    }
}
