package com.wallettransfer.outbox.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "destination_topic", nullable = false)
    private String destinationTopic;

    @Column(name = "correlation_id")
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxEvent() {}

    public OutboxEvent(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            String payload,
            String correlationId,
            Instant now) {
        this(
                id,
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                payload,
                correlationId,
                "wallet.transfer.events.v1",
                now);
    }

    public OutboxEvent(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            String payload,
            String correlationId,
            String destinationTopic,
            Instant now) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.correlationId = correlationId;
        this.destinationTopic = destinationTopic;
        this.status = OutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public void claim(Instant now) {
        status = OutboxStatus.PROCESSING;
        claimedAt = now;
    }

    public void published(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    public void failed(Instant now, Instant retryAt, String error, int maxAttempts) {
        attemptCount++;
        lastError = error == null ? "Unknown publication failure" : error.substring(0, Math.min(error.length(), 1000));
        claimedAt = null;
        if (attemptCount >= maxAttempts) {
            status = OutboxStatus.DEAD;
            nextAttemptAt = now;
        } else {
            status = OutboxStatus.FAILED;
            nextAttemptAt = retryAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getPayload() {
        return payload;
    }

    public String getDestinationTopic() {
        return destinationTopic;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
