package com.wallettransfer.providers.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_interactions")
public class ProviderInteraction {
    @Id
    private UUID id;

    @Column(name = "external_transfer_id")
    private UUID transferId;

    private String provider;

    @Column(name = "provider_request_reference")
    private String requestReference;

    @Column(name = "interaction_type")
    private String interactionType;

    @Column(name = "request_payload_masked")
    private String requestPayload;

    @Column(name = "response_payload_masked")
    private String responsePayload;

    @Column(name = "response_code")
    private Integer responseCode;

    private String outcome;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ProviderInteraction() {}

    public ProviderInteraction(
            UUID id,
            UUID transferId,
            String requestReference,
            String maskedToken,
            String outcome,
            Integer code,
            String response,
            long duration,
            Instant start,
            Instant end) {
        this.id = id;
        this.transferId = transferId;
        provider = "SIMULATOR";
        this.requestReference = requestReference;
        interactionType = "CREATE_TRANSFER";
        requestPayload = "beneficiary=" + maskedToken;
        responsePayload = response;
        responseCode = code;
        this.outcome = outcome;
        durationMs = duration;
        createdAt = start;
        completedAt = end;
    }
}
