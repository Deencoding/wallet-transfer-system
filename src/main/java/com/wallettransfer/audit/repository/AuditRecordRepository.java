package com.wallettransfer.audit.repository;

import com.wallettransfer.audit.model.AuditRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {
    long countByEventId(UUID eventId);
}
