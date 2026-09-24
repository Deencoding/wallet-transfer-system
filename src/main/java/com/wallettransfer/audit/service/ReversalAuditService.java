package com.wallettransfer.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.audit.model.*;
import com.wallettransfer.audit.repository.AuditRecordRepository;
import java.time.*;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class ReversalAuditService {
    private final AuditRecordRepository records;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ReversalAuditService(AuditRecordRepository records, ObjectMapper mapper, Clock clock) {
        this.records = records;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void record(UUID reversalId, UUID actorId, UUID transferId, String transferReference, String reason) {
        try {
            Map<String, String> auditDetails = Map.of("originalTransferReference", transferReference, "reason", reason);
            String details = mapper.writeValueAsString(auditDetails);
            AuditRecord record = new AuditRecord(
                    UUID.randomUUID(),
                    reversalId,
                    actorId,
                    AuditAction.TRANSFER_REVERSED,
                    "TRANSFER",
                    transferId,
                    MDC.get("correlationId"),
                    details,
                    clock.instant(),
                    clock.instant());
            records.save(record);
        } catch (Exception error) {
            throw new IllegalStateException("Could not create reversal audit record", error);
        }
    }
}
