package com.wallettransfer.providersimulator.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulated_provider_transfers")
public class SimulatedProviderTransfer {
    @Id
    private UUID id;

    @Column(name = "provider_request_reference")
    private String requestReference;

    @Column(name = "provider_transfer_reference")
    private String providerReference;

    @Column(name = "beneficiary_token")
    private String beneficiaryToken;

    private BigDecimal amount;
    private String currency;
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SimulatedProviderTransfer() {}

    public SimulatedProviderTransfer(
            UUID id,
            String request,
            String provider,
            String token,
            BigDecimal amount,
            String currency,
            String status,
            Instant now) {
        this.id = id;
        requestReference = request;
        providerReference = provider;
        beneficiaryToken = token;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        createdAt = now;
        updatedAt = now;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestReference() {
        return requestReference;
    }
}
