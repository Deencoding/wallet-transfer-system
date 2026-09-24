package com.wallettransfer.reconciliation.repository;

import com.wallettransfer.reconciliation.model.ReconciliationCase;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReconciliationCaseRepository extends JpaRepository<ReconciliationCase, UUID> {
    Page<ReconciliationCase> findAllByOrderByLastDetectedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReconciliationCase c where c.id=:id")
    Optional<ReconciliationCase> findByIdForUpdate(@Param("id") UUID id);
}
