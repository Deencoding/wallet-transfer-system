package com.wallettransfer.providers.repository;

import com.wallettransfer.providers.model.ProviderWebhookEvent;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProviderWebhookEventRepository extends JpaRepository<ProviderWebhookEvent, UUID> {
    @Modifying
    @Query(
            value =
                    "INSERT INTO provider_webhook_events(id,provider,provider_event_id,payload_hash,received_at,processed_at) VALUES(:id,:provider,:eventId,:hash,:now,:now) ON CONFLICT(provider,provider_event_id) DO NOTHING",
            nativeQuery = true)
    int claim(
            @Param("id") UUID id,
            @Param("provider") String provider,
            @Param("eventId") String eventId,
            @Param("hash") String hash,
            @Param("now") Instant now);

    Optional<ProviderWebhookEvent> findByProviderAndProviderEventId(String provider, String eventId);
}
