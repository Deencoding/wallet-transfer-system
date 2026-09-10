package com.wallettransfer.externaltransfers.repository;

import com.wallettransfer.externaltransfers.model.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ExternalTransferRepository extends JpaRepository<ExternalTransfer, UUID> {
    Optional<ExternalTransfer> findByOwnerIdAndIdempotencyKey(UUID ownerId, String key);

    Optional<ExternalTransfer> findByOwnerIdAndReference(UUID ownerId, String reference);

    Page<ExternalTransfer> findByOwnerId(UUID ownerId, Pageable pageable);

    Optional<ExternalTransfer> findByProviderRequestReference(String reference);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExternalTransfer e where e.id=:id")
    Optional<ExternalTransfer> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value =
                    "SELECT * FROM external_transfers WHERE status='PENDING_PROVIDER_CONFIRMATION' AND ((next_reconciliation_at<=:now AND reconciliation_claimed_at IS NULL) OR reconciliation_claimed_at<:expired) ORDER BY next_reconciliation_at NULLS FIRST FOR UPDATE SKIP LOCKED LIMIT :limit",
            nativeQuery = true)
    List<ExternalTransfer> findReconciliationCandidates(
            @Param("now") Instant now, @Param("expired") Instant expired, @Param("limit") int limit);
}
