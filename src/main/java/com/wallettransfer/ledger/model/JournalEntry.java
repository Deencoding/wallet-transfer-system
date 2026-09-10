package com.wallettransfer.ledger.model;

import com.wallettransfer.shared.money.Currency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {
    @Id
    private UUID id;

    @Column(name = "journal_transaction_id", nullable = false)
    private UUID journalTransactionId;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Column(name = "entry_sequence", nullable = false)
    private short entrySequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JournalEntry() {}

    public JournalEntry(
            UUID id,
            UUID journalId,
            UUID accountId,
            short sequence,
            EntryType type,
            BigDecimal amount,
            Currency currency,
            Instant now) {
        this.id = id;
        this.journalTransactionId = journalId;
        this.ledgerAccountId = accountId;
        this.entrySequence = sequence;
        this.entryType = type;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = now;
    }
}
