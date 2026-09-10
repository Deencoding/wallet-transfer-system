ALTER TABLE external_transfers DROP CONSTRAINT ck_external_status;
ALTER TABLE external_transfers ADD CONSTRAINT ck_external_status CHECK(status IN('PENDING','PROCESSING','PENDING_PROVIDER_CONFIRMATION','MANUAL_REVIEW','SUCCESSFUL','FAILED'));
ALTER TABLE external_transfers ADD COLUMN uncertain_since TIMESTAMPTZ;
ALTER TABLE external_transfers ADD COLUMN next_reconciliation_at TIMESTAMPTZ;
ALTER TABLE external_transfers ADD COLUMN reconciliation_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE external_transfers ADD COLUMN last_status_check_at TIMESTAMPTZ;
ALTER TABLE external_transfers ADD COLUMN last_provider_error VARCHAR(1000);
ALTER TABLE external_transfers ADD COLUMN reconciliation_claimed_at TIMESTAMPTZ;
ALTER TABLE external_transfers ADD CONSTRAINT ck_external_reconciliation_attempts CHECK(reconciliation_attempts>=0);
ALTER TABLE external_transfers ADD CONSTRAINT ck_external_uncertain CHECK(status NOT IN('PENDING_PROVIDER_CONFIRMATION','MANUAL_REVIEW') OR uncertain_since IS NOT NULL);
CREATE INDEX ix_external_reconciliation_due ON external_transfers(status,next_reconciliation_at) WHERE status='PENDING_PROVIDER_CONFIRMATION';

CREATE TABLE provider_reconciliation_attempts(
 id UUID PRIMARY KEY, external_transfer_id UUID NOT NULL REFERENCES external_transfers(id),
 provider VARCHAR(50) NOT NULL, attempt_number INTEGER NOT NULL, query_reference VARCHAR(100) NOT NULL,
 outcome VARCHAR(40) NOT NULL, response_code INTEGER, error_category VARCHAR(50),
 error_message_masked VARCHAR(500), started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uq_reconciliation_attempt UNIQUE(external_transfer_id,attempt_number),
 CONSTRAINT ck_reconciliation_attempt_number CHECK(attempt_number>0)
);

CREATE OR REPLACE FUNCTION escalate_unresolved_external_transfer() RETURNS trigger AS $$
BEGIN
 IF NEW.status='PENDING_PROVIDER_CONFIRMATION' AND NEW.reconciliation_attempts>=8 THEN
  NEW.status='MANUAL_REVIEW'; NEW.next_reconciliation_at=NULL; NEW.reconciliation_claimed_at=NULL;
 END IF;
 RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER external_transfer_manual_review BEFORE UPDATE ON external_transfers
FOR EACH ROW EXECUTE FUNCTION escalate_unresolved_external_transfer();
