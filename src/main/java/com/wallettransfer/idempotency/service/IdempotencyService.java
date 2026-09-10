package com.wallettransfer.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.idempotency.exception.*;
import com.wallettransfer.idempotency.fingerprint.TransferRequestFingerprinter;
import com.wallettransfer.idempotency.repository.IdempotencyRecordRepository;
import com.wallettransfer.transfers.dto.*;
import com.wallettransfer.transfers.service.TransferService;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {
    private static final String ENDPOINT = "POST:/api/v1/transfers";
    private final IdempotencyRecordRepository records;
    private final TransferRequestFingerprinter fingerprints;
    private final TransferService transfers;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IdempotencyService(
            IdempotencyRecordRepository records,
            TransferRequestFingerprinter fingerprints,
            TransferService transfers,
            ObjectMapper mapper,
            Clock clock) {
        this.records = records;
        this.fingerprints = fingerprints;
        this.transfers = transfers;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public TransferResponse executeTransfer(UUID userId, String key, CreateTransferRequest request) {
        validate(key);
        String fingerprint = fingerprints.fingerprint(request);
        var now = clock.instant();
        UUID id = UUID.randomUUID();
        int claimed = records.claim(id, userId, ENDPOINT, key, fingerprint, now, now.plus(Duration.ofHours(24)));
        if (claimed == 0) {
            var existing = records.findByClientIdentityAndEndpointAndIdempotencyKey(userId, ENDPOINT, key)
                    .orElseThrow();
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyConflictException();
            }
            try {
                return mapper.readValue(existing.getResponseBody(), TransferResponse.class);
            } catch (Exception e) {
                throw new IllegalStateException("Stored idempotency response is invalid", e);
            }
        }
        TransferResponse response = transfers.create(userId, request, key, id);
        try {
            String body = mapper.writeValueAsString(response);
            var record = records.findById(id).orElseThrow();
            record.complete(body, response.reference(), clock.instant());
            return response;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void validate(String key) {
        if (key == null
                || key.length() < 8
                || key.length() > 255
                || !key.equals(key.trim())
                || key.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidIdempotencyKeyException();
        }
    }
}
