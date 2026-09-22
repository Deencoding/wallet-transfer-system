package com.wallettransfer.ledger.service;

import com.wallettransfer.ledger.dto.*;
import com.wallettransfer.ledger.exception.*;
import com.wallettransfer.ledger.model.*;
import com.wallettransfer.ledger.repository.*;
import com.wallettransfer.shared.money.Currency;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private final LedgerAccountRepository accounts;
    private final JournalTransactionRepository journals;
    private final JournalEntryRepository entries;
    private final Clock clock;

    public LedgerService(
            LedgerAccountRepository accounts,
            JournalTransactionRepository journals,
            JournalEntryRepository entries,
            Clock clock) {
        this.accounts = accounts;
        this.journals = journals;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional
    public void createWalletAccount(UUID walletId, Currency currency) {
        if (accounts.existsByWalletId(walletId)) {
            return;
        }
        LedgerAccount account = new LedgerAccount(UUID.randomUUID(), walletId, currency, clock.instant());
        accounts.saveAndFlush(account);
    }

    @Transactional
    public LedgerPostingResult post(LedgerPostingCommand command) {
        if (command.entries() == null || command.entries().size() < 2)
            throw new InvalidLedgerEntryException("A journal requires at least two entries");
        BigDecimal debits = BigDecimal.ZERO, credits = BigDecimal.ZERO;
        Map<UUID, LedgerAccount> loaded = new HashMap<>();
        for (LedgerEntryCommand line : command.entries()) {
            if (line.amount() == null
                    || line.amount().signum() <= 0
                    || line.amount().scale() > command.currency().scale())
                throw new InvalidLedgerEntryException("Entry amounts must be positive and scale 2");
            LedgerAccount account = loaded.computeIfAbsent(line.ledgerAccountId(), id -> accounts.findById(id)
                    .orElseThrow(LedgerAccountNotFoundException::new));
            if (account.getStatus() != LedgerAccountStatus.ACTIVE)
                throw new InvalidLedgerEntryException("Ledger account is closed");
            if (account.getCurrency() != command.currency()) {
                throw new LedgerCurrencyMismatchException();
            }
            if (line.type() == EntryType.DEBIT) {
                debits = debits.add(line.amount());
            } else {
                credits = credits.add(line.amount());
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new UnbalancedJournalException();
        }
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String ref = "JRN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        JournalTransaction journal = new JournalTransaction(
                id,
                ref,
                command.sourceType(),
                command.sourceReference(),
                command.currency(),
                command.description(),
                now);
        journals.save(journal);
        short sequence = 1;
        for (LedgerEntryCommand line : command.entries()) {
            UUID entryId = UUID.randomUUID();
            BigDecimal entryAmount = line.amount().setScale(command.currency().scale());
            JournalEntry entry = new JournalEntry(
                    entryId, id, line.ledgerAccountId(), sequence++, line.type(), entryAmount, command.currency(), now);
            entries.save(entry);
        }
        entries.flush();
        return new LedgerPostingResult(id, ref);
    }

    public LedgerAccount getAccountForWallet(UUID walletId) {
        return accounts.findByWalletId(walletId).orElseThrow(LedgerAccountNotFoundException::new);
    }

    @Transactional
    public void postTransfer(
            String transferReference,
            UUID senderWalletId,
            UUID receiverWalletId,
            BigDecimal amount,
            Currency currency,
            String description) {
        LedgerAccount sender = getAccountForWallet(senderWalletId);
        LedgerAccount receiver = getAccountForWallet(receiverWalletId);
        LedgerEntryCommand debitEntry = new LedgerEntryCommand(sender.getId(), EntryType.DEBIT, amount);
        LedgerEntryCommand creditEntry = new LedgerEntryCommand(receiver.getId(), EntryType.CREDIT, amount);
        List<LedgerEntryCommand> postingEntries = List.of(debitEntry, creditEntry);
        LedgerPostingCommand command = new LedgerPostingCommand(
                JournalSourceType.TRANSFER, transferReference, currency, description, postingEntries);
        post(command);
    }

    @Transactional
    public LedgerPostingResult postReversal(
            String reference,
            UUID originalReceiverId,
            UUID originalSenderId,
            BigDecimal amount,
            Currency currency,
            String reason) {
        LedgerAccount receiver = getAccountForWallet(originalReceiverId);
        LedgerAccount sender = getAccountForWallet(originalSenderId);
        LedgerEntryCommand debitEntry = new LedgerEntryCommand(receiver.getId(), EntryType.DEBIT, amount);
        LedgerEntryCommand creditEntry = new LedgerEntryCommand(sender.getId(), EntryType.CREDIT, amount);
        List<LedgerEntryCommand> postingEntries = List.of(debitEntry, creditEntry);
        LedgerPostingCommand command =
                new LedgerPostingCommand(JournalSourceType.REVERSAL, reference, currency, reason, postingEntries);
        return post(command);
    }
}
