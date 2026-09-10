package com.wallettransfer.shared.security.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "principal_hash")
    private String principalHash;

    @Column(name = "client_address_hash")
    private String clientAddressHash;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metadata;

    protected SecurityEvent() {}

    public SecurityEvent(
            UUID id,
            String eventType,
            String outcome,
            UUID actorId,
            String principalHash,
            String clientAddressHash,
            String resourceType,
            UUID resourceId,
            String reasonCode,
            String correlationId,
            Instant occurredAt,
            String metadata) {
        this.id = id;
        this.eventType = eventType;
        this.outcome = outcome;
        this.actorId = actorId;
        this.principalHash = principalHash;
        this.clientAddressHash = clientAddressHash;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.reasonCode = reasonCode;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.metadata = metadata;
    }
}
