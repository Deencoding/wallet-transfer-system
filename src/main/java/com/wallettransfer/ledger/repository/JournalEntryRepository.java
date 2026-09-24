package com.wallettransfer.ledger.repository;

import com.wallettransfer.ledger.model.JournalEntry;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    @Query(
            value =
                    "SELECT COALESCE(sum(CASE WHEN e.entry_type='CREDIT' THEN e.amount ELSE -e.amount END),0) FROM journal_entries e WHERE e.ledger_account_id=:id",
            nativeQuery = true)
    BigDecimal calculateLiabilityBalance(@Param("id") UUID accountId);
}
