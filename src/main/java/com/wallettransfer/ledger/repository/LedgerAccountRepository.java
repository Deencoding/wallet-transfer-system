package com.wallettransfer.ledger.repository;

import com.wallettransfer.ledger.model.LedgerAccount;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByWalletId(UUID walletId);

    Optional<LedgerAccount> findByAccountCode(String accountCode);

    boolean existsByWalletId(UUID walletId);
}
