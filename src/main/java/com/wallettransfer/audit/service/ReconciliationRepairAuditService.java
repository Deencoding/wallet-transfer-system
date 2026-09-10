package com.wallettransfer.audit.service;

import com.wallettransfer.audit.model.*;
import com.wallettransfer.audit.repository.AuditRecordRepository;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationRepairAuditService {
    private final AuditRecordRepository records;
    private final Clock clock;

    public ReconciliationRepairAuditService(AuditRecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    public void record(UUID repairId, UUID actor, UUID walletId, String details) {
        records.save(new AuditRecord(
                UUID.randomUUID(),
                repairId,
                actor,
                AuditAction.RECONCILIATION_REPAIR,
                "WALLET",
                walletId,
                org.slf4j.MDC.get("correlationId"),
                details,
                clock.instant(),
                clock.instant()));
    }
}
