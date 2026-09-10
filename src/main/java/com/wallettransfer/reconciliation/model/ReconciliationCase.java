package com.wallettransfer.reconciliation.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reconciliation_cases")
public class ReconciliationCase {
    @Id
    private UUID id;

    @Column(name = "case_key")
    private String caseKey;

    @Column(name = "last_run_id")
    private UUID lastRunId;

    private String category;
    private String severity;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_value", columnDefinition = "jsonb")
    private String expectedValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actual_value", columnDefinition = "jsonb")
    private String actualValue;

    private String status;

    @Column(name = "first_detected_at")
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at")
    private Instant lastDetectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_type")
    private String resolutionType;

    @Column(name = "resolution_reason")
    private String resolutionReason;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Version
    private long version;

    protected ReconciliationCase() {}

    public UUID getId() {
        return id;
    }

    public String getCaseKey() {
        return caseKey;
    }

    public String getCategory() {
        return category;
    }

    public String getSeverity() {
        return severity;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public String getActualValue() {
        return actualValue;
    }

    public String getStatus() {
        return status;
    }

    public Instant getLastDetectedAt() {
        return lastDetectedAt;
    }
}
