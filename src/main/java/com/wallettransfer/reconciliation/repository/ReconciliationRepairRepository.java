package com.wallettransfer.reconciliation.repository;

import com.wallettransfer.reconciliation.model.ReconciliationRepair;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRepairRepository extends JpaRepository<ReconciliationRepair, UUID> {
    Optional<ReconciliationRepair> findByRequestedByAndKey(UUID actor, String key);
}
