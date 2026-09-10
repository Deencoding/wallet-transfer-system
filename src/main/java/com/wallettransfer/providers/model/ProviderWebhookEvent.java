package com.wallettransfer.providers.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_webhook_events")
public class ProviderWebhookEvent {
    @Id
    private UUID id;

    private String provider;

    @Column(name = "provider_event_id")
    private String providerEventId;

    @Column(name = "payload_hash")
    private String payloadHash;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected ProviderWebhookEvent() {}

    public String getPayloadHash() {
        return payloadHash;
    }
}
