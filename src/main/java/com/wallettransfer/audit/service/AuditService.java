package com.wallettransfer.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.audit.model.*;
import com.wallettransfer.audit.repository.AuditRecordRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditRecordRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AuditService(AuditRecordRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void transferCompleted(
            UUID eventId,
            UUID actorId,
            UUID transferId,
            String correlationId,
            String reference,
            String amount,
            String currency,
            Instant occurredAt) {
        try {
            String details = mapper.writeValueAsString(
                    Map.of("reference", reference, "amount", amount, "currency", currency, "status", "SUCCESSFUL"));
            repository.save(new AuditRecord(
                    UUID.randomUUID(),
                    eventId,
                    actorId,
                    AuditAction.TRANSFER_COMPLETED,
                    "TRANSFER",
                    transferId,
                    correlationId,
                    details,
                    occurredAt,
                    clock.instant()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize audit details", e);
        }
    }
}
