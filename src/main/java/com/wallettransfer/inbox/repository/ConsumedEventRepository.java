package com.wallettransfer.inbox.repository;

import com.wallettransfer.inbox.model.ConsumedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {
    @Modifying
    @Query(
            value =
                    "INSERT INTO consumed_events(id,event_id,consumer_name,event_type,event_version,aggregate_id,status,received_at) VALUES(:id,:eventId,:consumer,:eventType,:version,:aggregateId,'PROCESSING',:now) ON CONFLICT(consumer_name,event_id) DO NOTHING",
            nativeQuery = true)
    int claim(
            @Param("id") UUID id,
            @Param("eventId") UUID eventId,
            @Param("consumer") String consumer,
            @Param("eventType") String eventType,
            @Param("version") int version,
            @Param("aggregateId") UUID aggregateId,
            @Param("now") Instant now);

    @Modifying
    @Query(
            value =
                    "UPDATE consumed_events SET status='PROCESSED',processed_at=:now WHERE consumer_name=:consumer AND event_id=:eventId",
            nativeQuery = true)
    int complete(@Param("consumer") String consumer, @Param("eventId") UUID eventId, @Param("now") Instant now);
}
