package com.wallettransfer.reconciliation.dto;

import com.wallettransfer.reconciliation.model.ReconciliationCase;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationCaseResponse(
        UUID id,
        String caseKey,
        String category,
        String severity,
        String resourceType,
        UUID resourceId,
        String expectedValue,
        String actualValue,
        String status,
        Instant lastDetectedAt) {
    public static ReconciliationCaseResponse from(ReconciliationCase value) {
        return new ReconciliationCaseResponse(
                value.getId(),
                value.getCaseKey(),
                value.getCategory(),
                value.getSeverity(),
                value.getResourceType(),
                value.getResourceId(),
                value.getExpectedValue(),
                value.getActualValue(),
                value.getStatus(),
                value.getLastDetectedAt());
    }
}
