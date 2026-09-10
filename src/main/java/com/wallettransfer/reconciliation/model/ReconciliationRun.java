package com.wallettransfer.reconciliation.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {
    @Id
    private UUID id;

    @Column(name = "run_type")
    private String runType;

    private String status;

    @Column(name = "started_by")
    private UUID startedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "scanned_count")
    private long scannedCount;

    @Column(name = "discrepancy_count")
    private long discrepancyCount;

    @Column(name = "failure_reason")
    private String failureReason;

    protected ReconciliationRun() {}

    public ReconciliationRun(UUID id, UUID actor, Instant now) {
        this.id = id;
        runType = "FULL";
        status = "RUNNING";
        startedBy = actor;
        startedAt = now;
    }

    public void complete(long scanned, long discrepancies, Instant now) {
        status = "COMPLETED";
        scannedCount = scanned;
        discrepancyCount = discrepancies;
        completedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getScannedCount() {
        return scannedCount;
    }

    public long getDiscrepancyCount() {
        return discrepancyCount;
    }
}
