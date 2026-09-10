package com.wallettransfer.reconciliation.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reconciliation_repairs")
public class ReconciliationRepair {
    @Id
    private UUID id;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "idempotency_key")
    private String key;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "request_fingerprint")
    private String fingerprint;

    @Column(name = "repair_type")
    private String repairType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", columnDefinition = "jsonb")
    private String beforeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot", columnDefinition = "jsonb")
    private String afterSnapshot;

    private String reason;
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ReconciliationRepair() {}

    public ReconciliationRepair(
            UUID id,
            UUID caseId,
            String key,
            UUID actor,
            String fingerprint,
            String before,
            String after,
            String reason,
            Instant now) {
        this.id = id;
        this.caseId = caseId;
        this.key = key;
        requestedBy = actor;
        this.fingerprint = fingerprint;
        repairType = "REBUILD_WALLET_PROJECTION";
        beforeSnapshot = before;
        afterSnapshot = after;
        this.reason = reason;
        status = "SUCCESSFUL";
        createdAt = now;
        completedAt = now;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public UUID getId() {
        return id;
    }
}
