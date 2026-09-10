ALTER TABLE audit_records DROP CONSTRAINT ck_audit_action;
ALTER TABLE audit_records ADD CONSTRAINT ck_audit_action CHECK(action IN('TRANSFER_COMPLETED','TRANSFER_REVERSED'));

CREATE TABLE transfer_reversals(
 id UUID PRIMARY KEY, reference VARCHAR(80) NOT NULL UNIQUE,
 original_transfer_id UUID NOT NULL UNIQUE REFERENCES transfers(id),
 original_transfer_reference VARCHAR(80) NOT NULL,
 amount NUMERIC(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
 reason VARCHAR(500) NOT NULL, requested_by UUID NOT NULL REFERENCES users(id),
 idempotency_key VARCHAR(255) NOT NULL, request_fingerprint VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL, reversal_journal_id UUID REFERENCES journal_transactions(id),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ,
 CONSTRAINT uq_reversal_request UNIQUE(requested_by,idempotency_key),
 CONSTRAINT ck_reversal_amount CHECK(amount>0), CONSTRAINT ck_reversal_currency CHECK(currency='NGN'),
 CONSTRAINT ck_reversal_status CHECK(status IN('PENDING','PROCESSING','SUCCESSFUL','FAILED')),
 CONSTRAINT ck_reversal_completed CHECK((status='SUCCESSFUL' AND reversal_journal_id IS NOT NULL AND completed_at IS NOT NULL) OR (status<>'SUCCESSFUL' AND reversal_journal_id IS NULL AND completed_at IS NULL))
);
CREATE INDEX ix_reversals_created ON transfer_reversals(created_at DESC);
