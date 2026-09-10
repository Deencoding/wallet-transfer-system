ALTER TABLE audit_records DROP CONSTRAINT ck_audit_action;
ALTER TABLE audit_records ADD CONSTRAINT ck_audit_action CHECK(action IN('TRANSFER_COMPLETED','TRANSFER_REVERSED','RECONCILIATION_REPAIR'));

CREATE TABLE reconciliation_runs(
 id UUID PRIMARY KEY, run_type VARCHAR(50) NOT NULL, status VARCHAR(20) NOT NULL,
 started_by UUID REFERENCES users(id), started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ,
 scanned_count BIGINT NOT NULL DEFAULT 0, discrepancy_count BIGINT NOT NULL DEFAULT 0,
 failure_reason VARCHAR(1000),
 CONSTRAINT ck_reconciliation_run_status CHECK(status IN('RUNNING','COMPLETED','FAILED'))
);

CREATE TABLE reconciliation_cases(
 id UUID PRIMARY KEY, case_key VARCHAR(250) NOT NULL UNIQUE, last_run_id UUID NOT NULL REFERENCES reconciliation_runs(id),
 category VARCHAR(100) NOT NULL, severity VARCHAR(20) NOT NULL, resource_type VARCHAR(50) NOT NULL,
 resource_id UUID NOT NULL, expected_value JSONB NOT NULL, actual_value JSONB NOT NULL,
 status VARCHAR(20) NOT NULL, first_detected_at TIMESTAMPTZ NOT NULL, last_detected_at TIMESTAMPTZ NOT NULL,
 resolved_at TIMESTAMPTZ, resolution_type VARCHAR(50), resolution_reason VARCHAR(1000),
 resolved_by UUID REFERENCES users(id), version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_reconciliation_case_status CHECK(status IN('OPEN','ACKNOWLEDGED','RESOLVED','IGNORED')),
 CONSTRAINT ck_reconciliation_severity CHECK(severity IN('INFO','WARNING','HIGH','CRITICAL'))
);
CREATE INDEX ix_reconciliation_cases_filter ON reconciliation_cases(status,severity,category,last_detected_at DESC);

CREATE TABLE reconciliation_repairs(
 id UUID PRIMARY KEY, case_id UUID NOT NULL REFERENCES reconciliation_cases(id),
 idempotency_key VARCHAR(255) NOT NULL, requested_by UUID NOT NULL REFERENCES users(id),
 request_fingerprint VARCHAR(64) NOT NULL, repair_type VARCHAR(100) NOT NULL,
 before_snapshot JSONB NOT NULL, after_snapshot JSONB NOT NULL, reason VARCHAR(1000) NOT NULL,
 status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ,
 CONSTRAINT uq_reconciliation_repair_request UNIQUE(requested_by,idempotency_key),
 CONSTRAINT ck_reconciliation_repair_status CHECK(status IN('SUCCESSFUL','FAILED'))
);
CREATE UNIQUE INDEX uq_successful_case_repair ON reconciliation_repairs(case_id,repair_type) WHERE status='SUCCESSFUL';
