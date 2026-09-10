package com.wallettransfer.ledger.model;

import com.wallettransfer.shared.money.Currency;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_transactions")
public class JournalTransaction {
    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private JournalSourceType sourceType;

    @Column(name = "source_reference", nullable = false, length = 100)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(length = 255)
    private String description;

    @Column(name = "reversal_of_journal_id")
    private UUID reversalOfJournalId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JournalTransaction() {}

    public JournalTransaction(
            UUID id,
            String reference,
            JournalSourceType sourceType,
            String sourceReference,
            Currency currency,
            String description,
            Instant now) {
        this.id = id;
        this.reference = reference;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
        this.currency = currency;
        this.description = description;
        this.postedAt = now;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }
}
