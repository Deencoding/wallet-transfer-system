package com.wallettransfer.providers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.externaltransfers.repository.ExternalTransferRepository;
import com.wallettransfer.externaltransfers.service.ExternalTransferCompletionService;
import com.wallettransfer.providers.dto.ProviderWebhookPayload;
import com.wallettransfer.providers.exception.InvalidWebhookSignatureException;
import com.wallettransfer.providers.exception.WebhookReplayConflictException;
import com.wallettransfer.providers.repository.ProviderWebhookEventRepository;
import com.wallettransfer.providers.security.WebhookSignatureVerifier;
import com.wallettransfer.shared.security.service.SecurityAuditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderWebhookService {

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private final WebhookSignatureVerifier signatures;
    private final ProviderWebhookEventRepository events;
    private final ExternalTransferRepository transfers;
    private final ExternalTransferCompletionService completion;
    private final SecurityAuditService securityAudit;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ProviderWebhookService(
            WebhookSignatureVerifier signatures,
            ProviderWebhookEventRepository events,
            ExternalTransferRepository transfers,
            ExternalTransferCompletionService completion,
            SecurityAuditService securityAudit,
            ObjectMapper mapper,
            Clock clock) {
        this.signatures = signatures;
        this.events = events;
        this.transfers = transfers;
        this.completion = completion;
        this.securityAudit = securityAudit;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public boolean process(String provider, String eventId, String timestamp, String signature, String rawBody) {
        validateEnvelope(provider, eventId, rawBody);
        try {
            signatures.verify(timestamp, rawBody, signature);
        } catch (InvalidWebhookSignatureException invalid) {
            securityAudit.rejected("WEBHOOK_AUTHENTICATION", provider, "unknown", "INVALID_SIGNATURE");
            throw invalid;
        }
        try {
            String normalizedProvider = provider.toUpperCase();
            String hash = hash(rawBody);
            if (events.claim(UUID.randomUUID(), normalizedProvider, eventId, hash, clock.instant()) == 0) {
                var existing = events.findByProviderAndProviderEventId(normalizedProvider, eventId)
                        .orElseThrow();
                if (!MessageDigest.isEqual(
                        existing.getPayloadHash().getBytes(StandardCharsets.US_ASCII),
                        hash.getBytes(StandardCharsets.US_ASCII))) {
                    securityAudit.rejected("WEBHOOK_REPLAY", provider, "unknown", "PAYLOAD_CONFLICT");
                    throw new WebhookReplayConflictException();
                }
                return false;
            }
            ProviderWebhookPayload payload = mapper.readValue(rawBody, ProviderWebhookPayload.class);
            validatePayload(payload);
            var transfer = transfers
                    .findByProviderRequestReference(payload.providerRequestReference())
                    .orElseThrow();
            if ("SUCCESSFUL".equals(payload.status())) {
                completion.successful(transfer.getId(), payload.providerTransferReference());
            } else {
                completion.failed(transfer.getId(), payload.failureReason());
            }
            return true;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid webhook payload", exception);
        }
    }

    private void validateEnvelope(String provider, String eventId, String rawBody) {
        if (!"simulator".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("Unknown provider");
        }
        if (eventId == null || !eventId.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Invalid provider event identifier");
        }
        if (rawBody == null || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Invalid webhook body");
        }
    }

    private void validatePayload(ProviderWebhookPayload payload) {
        if (payload.providerRequestReference() == null
                || payload.providerRequestReference().length() > 128
                || payload.providerTransferReference() == null
                || payload.providerTransferReference().length() > 128
                || !("SUCCESSFUL".equals(payload.status()) || "FAILED".equals(payload.status()))
                || payload.failureReason() != null && payload.failureReason().length() > 500) {
            throw new IllegalArgumentException("Invalid webhook payload");
        }
    }

    private String hash(String rawBody) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(rawBody.getBytes(StandardCharsets.UTF_8)));
    }
}
