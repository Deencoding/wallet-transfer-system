package com.wallettransfer.ledger.service;

import com.wallettransfer.ledger.dto.*;
import com.wallettransfer.ledger.exception.*;
import com.wallettransfer.ledger.model.*;
import com.wallettransfer.ledger.repository.*;
import com.wallettransfer.shared.money.Currency;
import java.math.BigDecimal;
import java.time.Clock;
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
        accounts.saveAndFlush(new LedgerAccount(UUID.randomUUID(), walletId, currency, clock.instant()));
    }

    @Transactional
    public LedgerPostingResult post(LedgerPostingCommand command) {
        if (command.entries() == null || command.entries().size() < 2)
            throw new InvalidLedgerEntryException("A journal requires at least two entries");
        BigDecimal debits = BigDecimal.ZERO, credits = BigDecimal.ZERO;
        Map<UUID, LedgerAccount> loaded = new HashMap<>();
        for (var line : command.entries()) {
            if (line.amount() == null
                    || line.amount().signum() <= 0
                    || line.amount().scale() > command.currency().scale())
                throw new InvalidLedgerEntryException("Entry amounts must be positive and scale 2");
            var account = loaded.computeIfAbsent(line.ledgerAccountId(), id -> accounts.findById(id)
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
        var now = clock.instant();
        UUID id = UUID.randomUUID();
        String ref = "JRN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        journals.save(new JournalTransaction(
                id,
                ref,
                command.sourceType(),
                command.sourceReference(),
                command.currency(),
                command.description(),
                now));
        short sequence = 1;
        for (var line : command.entries())
            entries.save(new JournalEntry(
                    UUID.randomUUID(),
                    id,
                    line.ledgerAccountId(),
                    sequence++,
                    line.type(),
                    line.amount().setScale(command.currency().scale()),
                    command.currency(),
                    now));
        entries.flush();
        return new LedgerPostingResult(id, ref);
    }

    public LedgerAccount getAccountForWallet(UUID walletId) {
        return accounts.findByWalletId(walletId).orElseThrow(LedgerAccountNotFoundException::new);
    }

    @Transactional
    public LedgerPostingResult postTransfer(
            String transferReference,
            UUID senderWalletId,
            UUID receiverWalletId,
            BigDecimal amount,
            Currency currency,
            String description) {
        var sender = getAccountForWallet(senderWalletId);
        var receiver = getAccountForWallet(receiverWalletId);
        return post(new LedgerPostingCommand(
                JournalSourceType.TRANSFER,
                transferReference,
                currency,
                description,
                List.of(
                        new LedgerEntryCommand(sender.getId(), EntryType.DEBIT, amount),
                        new LedgerEntryCommand(receiver.getId(), EntryType.CREDIT, amount))));
    }

    @Transactional
    public LedgerPostingResult postExternalTransfer(
            String reference, UUID walletId, BigDecimal amount, Currency currency, String description) {
        var wallet = getAccountForWallet(walletId);
        var settlement = accounts.findByAccountCode("PLATFORM-NGN-EXTERNAL-SETTLEMENT")
                .orElseThrow(LedgerAccountNotFoundException::new);
        return post(new LedgerPostingCommand(
                JournalSourceType.EXTERNAL_TRANSFER,
                reference,
                currency,
                description,
                List.of(
                        new LedgerEntryCommand(wallet.getId(), EntryType.DEBIT, amount),
                        new LedgerEntryCommand(settlement.getId(), EntryType.CREDIT, amount))));
    }

    @Transactional
    public LedgerPostingResult postReversal(
            String reference,
            UUID originalReceiverId,
            UUID originalSenderId,
            BigDecimal amount,
            Currency currency,
            String reason) {
        var receiver = getAccountForWallet(originalReceiverId);
        var sender = getAccountForWallet(originalSenderId);
        return post(new LedgerPostingCommand(
                JournalSourceType.REVERSAL,
                reference,
                currency,
                reason,
                List.of(
                        new LedgerEntryCommand(receiver.getId(), EntryType.DEBIT, amount),
                        new LedgerEntryCommand(sender.getId(), EntryType.CREDIT, amount))));
    }
}
