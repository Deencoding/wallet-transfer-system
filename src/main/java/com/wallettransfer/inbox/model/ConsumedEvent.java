package com.wallettransfer.inbox.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumed_events")
public class ConsumedEvent {
    @Id
    private UUID id;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "consumer_name")
    private String consumerName;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "event_version")
    private int eventVersion;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    private ConsumedEventStatus status;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    protected ConsumedEvent() {}
}
