package com.wallettransfer.reconciliation.repository;

import com.wallettransfer.reconciliation.model.ProviderReconciliationAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderReconciliationAttemptRepository extends JpaRepository<ProviderReconciliationAttempt, UUID> {}
