package com.wallettransfer.ledger.service;

import com.wallettransfer.ledger.dto.LedgerDiscrepancy;
import com.wallettransfer.ledger.repository.*;
import com.wallettransfer.wallets.service.WalletService;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerReconciliationService {
    private final LedgerAccountRepository accounts;
    private final JournalEntryRepository entries;
    private final WalletService wallets;
    private final Clock clock;

    public LedgerReconciliationService(
            LedgerAccountRepository accounts, JournalEntryRepository entries, WalletService wallets, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.wallets = wallets;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<LedgerDiscrepancy> reconcile(UUID walletId) {
        var wallet = wallets.getLedgerSnapshot(walletId);
        var account = accounts.findByWalletId(walletId).orElseThrow();
        var calculated = entries.calculateLiabilityBalance(account.getId()).setScale(2);
        var stored = wallet.ledgerBalance();
        if (stored.compareTo(calculated) == 0) {
            return Optional.empty();
        }
        return Optional.of(new LedgerDiscrepancy(
                walletId, account.getId(), stored, calculated, calculated.subtract(stored), clock.instant()));
    }
}
