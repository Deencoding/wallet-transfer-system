package com.wallettransfer.reconciliation.dto;

import com.wallettransfer.reconciliation.model.ReconciliationRun;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationRunResponse(
        UUID id, String status, Instant startedAt, Instant completedAt, long scannedCount, long discrepancyCount) {
    public static ReconciliationRunResponse from(ReconciliationRun value) {
        return new ReconciliationRunResponse(
                value.getId(),
                value.getStatus(),
                value.getStartedAt(),
                value.getCompletedAt(),
                value.getScannedCount(),
                value.getDiscrepancyCount());
    }
}
