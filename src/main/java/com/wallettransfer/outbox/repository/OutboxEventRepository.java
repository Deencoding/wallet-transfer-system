package com.wallettransfer.outbox.repository;

import com.wallettransfer.outbox.model.OutboxEvent;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(
            value =
                    "SELECT * FROM outbox_events WHERE ((status IN ('PENDING','FAILED') AND next_attempt_at<=:now) OR (status='PROCESSING' AND claimed_at<:leaseExpired)) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT :batchSize",
            nativeQuery = true)
    List<OutboxEvent> findClaimable(
            @Param("now") Instant now, @Param("leaseExpired") Instant leaseExpired, @Param("batchSize") int batchSize);
}
