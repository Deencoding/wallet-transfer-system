package com.wallettransfer.reconciliation.repository;

import com.wallettransfer.reconciliation.model.ReconciliationRun;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
    Page<ReconciliationRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
