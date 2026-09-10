package com.wallettransfer.shared.observability.model;

public record OperationalBacklogSnapshot(
        long pendingOutbox,
        long processingOutbox,
        long deadOutbox,
        double oldestPendingAgeSeconds,
        long openReconciliationCases,
        long criticalReconciliationCases) {

    public static OperationalBacklogSnapshot empty() {
        return new OperationalBacklogSnapshot(0, 0, 0, 0, 0, 0);
    }
}
