package com.wallettransfer.idempotency.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    private UUID id;

    @Column(name = "client_identity")
    private UUID clientIdentity;

    private String endpoint;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "resource_reference")
    private String resourceReference;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getResourceReference() {
        return resourceReference;
    }

    public UUID getId() {
        return id;
    }

    public void complete(String body, String reference, Instant now) {
        status = IdempotencyStatus.COMPLETED;
        responseStatus = 201;
        responseBody = body;
        resourceReference = reference;
        updatedAt = now;
    }
}
