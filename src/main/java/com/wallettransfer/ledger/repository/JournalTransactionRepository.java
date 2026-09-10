package com.wallettransfer.ledger.repository;

import com.wallettransfer.ledger.model.JournalTransaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalTransactionRepository extends JpaRepository<JournalTransaction, UUID> {}
