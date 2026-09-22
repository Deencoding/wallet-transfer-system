-- Initial schema for internal wallet transfers. Requires an empty database schema.

CREATE TABLE schema_metadata (
    id SMALLINT PRIMARY KEY,
    description VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_schema_metadata_singleton CHECK (id = 1)
);

INSERT INTO schema_metadata (id, description)
VALUES (1, 'Wallet transfer system schema initialized');

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_users_email CHECK (email = lower(btrim(email)))
);

CREATE TABLE roles (
    id SMALLINT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('CUSTOMER', 'ADMIN', 'SUPPORT'))
);

INSERT INTO roles (id, name) VALUES
    (1, 'CUSTOMER'),
    (2, 'ADMIN'),
    (3, 'SUPPORT');

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id SMALLINT NOT NULL REFERENCES roles(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    jwt_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    revocation_reason VARCHAR(30),
    replaced_by_token_id UUID REFERENCES refresh_tokens(id),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_refresh_tokens_jwt_id UNIQUE (jwt_id),
    CONSTRAINT uq_refresh_tokens_fingerprint UNIQUE (token_fingerprint),
    CONSTRAINT ck_refresh_tokens_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_refresh_tokens_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL AND revocation_reason IS NULL)
        OR (status <> 'ACTIVE' AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX ix_refresh_tokens_user_status ON refresh_tokens(user_id, status);
CREATE INDEX ix_refresh_tokens_family_status ON refresh_tokens(family_id, status);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens(expires_at);

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    available_balance NUMERIC(19,2) NOT NULL,
    ledger_balance NUMERIC(19,2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_wallets_owner_currency UNIQUE (owner_id, currency),
    CONSTRAINT ck_wallets_currency CHECK (currency IN ('NGN')),
    CONSTRAINT ck_wallets_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_wallets_available_non_negative CHECK (available_balance >= 0),
    CONSTRAINT ck_wallets_ledger_non_negative CHECK (ledger_balance >= 0),
    CONSTRAINT ck_wallets_available_not_above_ledger CHECK (available_balance <= ledger_balance)
);

CREATE INDEX ix_wallets_owner_id ON wallets(owner_id);
CREATE INDEX ix_wallets_status ON wallets(status);

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    wallet_id UUID REFERENCES wallets(id),
    account_code VARCHAR(80) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_ledger_accounts_wallet UNIQUE (wallet_id),
    CONSTRAINT uq_ledger_accounts_code UNIQUE (account_code),
    CONSTRAINT ck_ledger_accounts_type CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    CONSTRAINT ck_ledger_accounts_currency CHECK (currency IN ('NGN')),
    CONSTRAINT ck_ledger_account_ownership CHECK (
        (account_type = 'LIABILITY' AND wallet_id IS NOT NULL) OR
        (account_type <> 'LIABILITY' AND wallet_id IS NULL)
    ),
    CONSTRAINT ck_ledger_accounts_status CHECK (status IN ('ACTIVE','CLOSED'))
);

CREATE TABLE journal_transactions (
    id UUID PRIMARY KEY,
    reference VARCHAR(80) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_reference VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    reversal_of_journal_id UUID REFERENCES journal_transactions(id),
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_journal_reference UNIQUE (reference),
    CONSTRAINT uq_journal_source UNIQUE (source_type, source_reference),
    CONSTRAINT uq_journal_reversal UNIQUE (reversal_of_journal_id),
    CONSTRAINT ck_journal_source_type CHECK (source_type IN ('TRANSFER','REVERSAL','ADJUSTMENT','OPENING_BALANCE')),
    CONSTRAINT ck_journal_currency CHECK (currency IN ('NGN'))
);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    journal_transaction_id UUID NOT NULL REFERENCES journal_transactions(id),
    ledger_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    entry_sequence SMALLINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_journal_entry_sequence UNIQUE (journal_transaction_id, entry_sequence),
    CONSTRAINT ck_journal_entry_sequence CHECK (entry_sequence > 0),
    CONSTRAINT ck_journal_entry_type CHECK (entry_type IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_journal_entry_amount CHECK (amount > 0),
    CONSTRAINT ck_journal_entry_currency CHECK (currency IN ('NGN'))
);

CREATE INDEX ix_journal_entries_journal ON journal_entries(journal_transaction_id);
CREATE INDEX ix_journal_entries_account ON journal_entries(ledger_account_id);
CREATE INDEX ix_journal_posted_at ON journal_transactions(posted_at);

CREATE FUNCTION validate_balanced_journal() RETURNS trigger AS $$
DECLARE journal_id UUID; debit_total NUMERIC(19,2); credit_total NUMERIC(19,2); entry_count INTEGER; invalid_currency INTEGER;
BEGIN
  journal_id := COALESCE(NEW.journal_transaction_id, OLD.journal_transaction_id);
  IF NOT EXISTS (SELECT 1 FROM journal_transactions WHERE id=journal_id) THEN RETURN NULL; END IF;
  SELECT count(*), COALESCE(sum(amount) FILTER (WHERE entry_type='DEBIT'),0),
         COALESCE(sum(amount) FILTER (WHERE entry_type='CREDIT'),0),
         count(*) FILTER (WHERE e.currency<>j.currency OR a.currency<>j.currency)
  INTO entry_count,debit_total,credit_total,invalid_currency
  FROM journal_entries e JOIN journal_transactions j ON j.id=e.journal_transaction_id
  JOIN ledger_accounts a ON a.id=e.ledger_account_id WHERE e.journal_transaction_id=journal_id;
  IF entry_count < 2 OR debit_total <> credit_total OR invalid_currency > 0 THEN
    RAISE EXCEPTION 'journal % is unbalanced or has invalid currency', journal_id;
  END IF;
  RETURN NULL;
END; $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER journal_balance_guard AFTER INSERT ON journal_entries
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_balanced_journal();

CREATE FUNCTION reject_ledger_mutation() RETURNS trigger AS $$ BEGIN
  RAISE EXCEPTION 'posted ledger records are immutable';
END; $$ LANGUAGE plpgsql;
CREATE TRIGGER immutable_journal_entries BEFORE UPDATE OR DELETE ON journal_entries
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
CREATE TRIGGER immutable_journal_transactions BEFORE UPDATE OR DELETE ON journal_transactions
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

INSERT INTO ledger_accounts(id,wallet_id,account_code,account_type,currency,status,created_at)
VALUES(gen_random_uuid(),NULL,'PLATFORM-NGN-SETTLEMENT','ASSET','NGN','ACTIVE',CURRENT_TIMESTAMP);

CREATE TABLE transfers (
 id UUID PRIMARY KEY,
 reference VARCHAR(80) NOT NULL,
 sender_wallet_id UUID NOT NULL REFERENCES wallets(id),
 receiver_wallet_id UUID NOT NULL REFERENCES wallets(id),
 amount NUMERIC(19,2) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 description VARCHAR(255),
 status VARCHAR(20) NOT NULL,
 failure_reason VARCHAR(50),
 idempotency_key VARCHAR(255),
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 completed_at TIMESTAMP WITH TIME ZONE,
 CONSTRAINT uq_transfers_reference UNIQUE(reference),
 CONSTRAINT ck_transfers_different_wallets CHECK(sender_wallet_id<>receiver_wallet_id),
 CONSTRAINT ck_transfers_amount CHECK(amount>0),
 CONSTRAINT ck_transfers_currency CHECK(currency IN('NGN')),
 CONSTRAINT ck_transfers_status CHECK(status IN('PENDING','PROCESSING','SUCCESSFUL','FAILED','REVERSED')),
 CONSTRAINT ck_transfers_failure CHECK((status='FAILED' AND failure_reason IS NOT NULL) OR (status<>'FAILED' AND failure_reason IS NULL)),
 CONSTRAINT ck_transfers_completion CHECK((status IN('SUCCESSFUL','FAILED','REVERSED') AND completed_at IS NOT NULL) OR (status IN('PENDING','PROCESSING') AND completed_at IS NULL))
);
CREATE INDEX ix_transfers_sender_created ON transfers(sender_wallet_id,created_at DESC);
CREATE INDEX ix_transfers_receiver_created ON transfers(receiver_wallet_id,created_at DESC);
CREATE INDEX ix_transfers_status_created ON transfers(status,created_at);

CREATE TABLE idempotency_records(
 id UUID PRIMARY KEY, client_identity UUID NOT NULL REFERENCES users(id), endpoint VARCHAR(150) NOT NULL,
 idempotency_key VARCHAR(255) NOT NULL, request_fingerprint VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL,
 response_status INTEGER, response_body JSONB, resource_reference VARCHAR(100), created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uq_idempotency_scope UNIQUE(client_identity,endpoint,idempotency_key),
 CONSTRAINT ck_idempotency_status CHECK(status IN('PROCESSING','COMPLETED')),
 CONSTRAINT ck_idempotency_expiry CHECK(expires_at>created_at),
 CONSTRAINT ck_idempotency_response CHECK((status='PROCESSING' AND response_status IS NULL AND response_body IS NULL) OR (status='COMPLETED' AND response_status IS NOT NULL AND response_body IS NOT NULL))
);
CREATE INDEX ix_idempotency_expiry ON idempotency_records(expires_at);
ALTER TABLE transfers ADD COLUMN idempotency_record_id UUID REFERENCES idempotency_records(id) ON DELETE SET NULL;
ALTER TABLE transfers ADD CONSTRAINT uq_transfer_idempotency_record UNIQUE(idempotency_record_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    destination_topic VARCHAR(200) NOT NULL DEFAULT 'wallet.transfer.events.v1',
    correlation_id VARCHAR(128),
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_outbox_logical_event UNIQUE (aggregate_type, aggregate_id, event_type, event_version),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','PROCESSING','FAILED','PUBLISHED','DEAD')),
    CONSTRAINT ck_outbox_version CHECK (event_version > 0),
    CONSTRAINT ck_outbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_processing_claim CHECK (status <> 'PROCESSING' OR claimed_at IS NOT NULL),
    CONSTRAINT ck_outbox_published_at CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX ix_outbox_poll ON outbox_events(status, next_attempt_at, created_at);
CREATE INDEX ix_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);
CREATE INDEX ix_outbox_created_at ON outbox_events(created_at);

CREATE TABLE consumed_events (
 id UUID PRIMARY KEY, event_id UUID NOT NULL, consumer_name VARCHAR(100) NOT NULL,
 event_type VARCHAR(100) NOT NULL, event_version INTEGER NOT NULL, aggregate_id UUID NOT NULL,
 status VARCHAR(20) NOT NULL, received_at TIMESTAMPTZ NOT NULL, processed_at TIMESTAMPTZ,
 failure_reason VARCHAR(1000),
 CONSTRAINT uq_consumed_event UNIQUE(consumer_name,event_id),
 CONSTRAINT ck_consumed_event_status CHECK(status IN('PROCESSING','PROCESSED')),
 CONSTRAINT ck_consumed_event_version CHECK(event_version>0),
 CONSTRAINT ck_consumed_event_processed CHECK(status<>'PROCESSED' OR processed_at IS NOT NULL)
);

CREATE TABLE notifications (
 id UUID PRIMARY KEY, event_id UUID NOT NULL, user_id UUID NOT NULL REFERENCES users(id),
 transfer_id UUID NOT NULL REFERENCES transfers(id), type VARCHAR(50) NOT NULL, status VARCHAR(20) NOT NULL,
 title VARCHAR(150) NOT NULL, message VARCHAR(500) NOT NULL, created_at TIMESTAMPTZ NOT NULL, sent_at TIMESTAMPTZ,
 CONSTRAINT uq_notification_event_recipient UNIQUE(event_id,user_id,type),
 CONSTRAINT ck_notification_type CHECK(type IN('TRANSFER_SENT','TRANSFER_RECEIVED')),
 CONSTRAINT ck_notification_status CHECK(status IN('PENDING','SENT','FAILED')),
 CONSTRAINT ck_notification_sent CHECK(status<>'SENT' OR sent_at IS NOT NULL)
);
CREATE INDEX ix_notifications_user_created ON notifications(user_id,created_at DESC);

CREATE TABLE audit_records (
 id UUID PRIMARY KEY, event_id UUID NOT NULL, actor_id UUID REFERENCES users(id),
 action VARCHAR(100) NOT NULL, resource_type VARCHAR(50) NOT NULL, resource_id UUID NOT NULL,
 correlation_id VARCHAR(128), details JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uq_audit_event_action UNIQUE(event_id,action),
 CONSTRAINT ck_audit_action CHECK(action IN('TRANSFER_COMPLETED','TRANSFER_REVERSED','RECONCILIATION_REPAIR'))
);
CREATE INDEX ix_audit_resource ON audit_records(resource_type,resource_id);
CREATE INDEX ix_audit_occurred ON audit_records(occurred_at DESC);

CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'audit records are immutable'; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER audit_records_immutable BEFORE UPDATE OR DELETE ON audit_records
FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();

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

CREATE TABLE security_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    actor_id UUID REFERENCES users(id),
    principal_hash VARCHAR(64),
    client_address_hash VARCHAR(64),
    resource_type VARCHAR(50),
    resource_id UUID,
    reason_code VARCHAR(80),
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_security_event_outcome CHECK (outcome IN ('SUCCESS','FAILURE','REJECTED'))
);

CREATE INDEX ix_security_events_type_time ON security_events(event_type, occurred_at DESC);
CREATE INDEX ix_security_events_actor_time ON security_events(actor_id, occurred_at DESC) WHERE actor_id IS NOT NULL;
CREATE INDEX ix_security_events_principal_time ON security_events(principal_hash, occurred_at DESC) WHERE principal_hash IS NOT NULL;
CREATE INDEX ix_security_events_outcome_time ON security_events(outcome, occurred_at DESC);

CREATE FUNCTION reject_security_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'security events are immutable';
END;
$$;

CREATE TRIGGER security_events_immutable
BEFORE UPDATE OR DELETE ON security_events
FOR EACH ROW EXECUTE FUNCTION reject_security_event_mutation();
