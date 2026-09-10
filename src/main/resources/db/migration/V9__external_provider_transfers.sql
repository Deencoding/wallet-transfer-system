ALTER TABLE outbox_events ADD COLUMN destination_topic VARCHAR(200) NOT NULL DEFAULT 'wallet.transfer.events.v1';
ALTER TABLE journal_transactions DROP CONSTRAINT ck_journal_source_type;
ALTER TABLE journal_transactions ADD CONSTRAINT ck_journal_source_type CHECK(source_type IN('TRANSFER','EXTERNAL_TRANSFER','REVERSAL','ADJUSTMENT','OPENING_BALANCE'));

INSERT INTO ledger_accounts(id,wallet_id,account_code,account_type,currency,status,created_at)
VALUES(gen_random_uuid(),NULL,'PLATFORM-NGN-EXTERNAL-SETTLEMENT','ASSET','NGN','ACTIVE',CURRENT_TIMESTAMP)
ON CONFLICT(account_code) DO NOTHING;

CREATE TABLE external_transfers(
 id UUID PRIMARY KEY, reference VARCHAR(80) NOT NULL UNIQUE, owner_id UUID NOT NULL REFERENCES users(id),
 sender_wallet_id UUID NOT NULL REFERENCES wallets(id), amount NUMERIC(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
 beneficiary_token VARCHAR(100) NOT NULL, provider VARCHAR(50) NOT NULL,
 provider_request_reference VARCHAR(100) NOT NULL UNIQUE, provider_transfer_reference VARCHAR(100) UNIQUE,
 status VARCHAR(40) NOT NULL, failure_reason VARCHAR(100), description VARCHAR(255),
 idempotency_key VARCHAR(255) NOT NULL, request_fingerprint VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uq_external_idempotency UNIQUE(owner_id,idempotency_key),
 CONSTRAINT ck_external_amount CHECK(amount>0), CONSTRAINT ck_external_currency CHECK(currency='NGN'),
 CONSTRAINT ck_external_status CHECK(status IN('PENDING','PROCESSING','PENDING_PROVIDER_CONFIRMATION','SUCCESSFUL','FAILED')),
 CONSTRAINT ck_external_completion CHECK((status IN('SUCCESSFUL','FAILED') AND completed_at IS NOT NULL) OR (status NOT IN('SUCCESSFUL','FAILED') AND completed_at IS NULL)),
 CONSTRAINT ck_external_provider_success CHECK(status<>'SUCCESSFUL' OR provider_transfer_reference IS NOT NULL)
);
CREATE INDEX ix_external_owner_created ON external_transfers(owner_id,created_at DESC);
CREATE INDEX ix_external_status_updated ON external_transfers(status,updated_at);

CREATE TABLE external_transfer_reservations(
 id UUID PRIMARY KEY, external_transfer_id UUID NOT NULL UNIQUE REFERENCES external_transfers(id),
 wallet_id UUID NOT NULL REFERENCES wallets(id), amount NUMERIC(19,2) NOT NULL,
 status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL, resolved_at TIMESTAMPTZ,
 CONSTRAINT ck_reservation_amount CHECK(amount>0),
 CONSTRAINT ck_reservation_status CHECK(status IN('ACTIVE','SETTLED','RELEASED')),
 CONSTRAINT ck_reservation_resolved CHECK((status='ACTIVE' AND resolved_at IS NULL) OR (status<>'ACTIVE' AND resolved_at IS NOT NULL))
);

CREATE TABLE provider_interactions(
 id UUID PRIMARY KEY, external_transfer_id UUID NOT NULL REFERENCES external_transfers(id), provider VARCHAR(50) NOT NULL,
 provider_request_reference VARCHAR(100) NOT NULL, interaction_type VARCHAR(30) NOT NULL,
 request_payload_masked VARCHAR(1000), response_payload_masked VARCHAR(1000), response_code INTEGER,
 outcome VARCHAR(40) NOT NULL, duration_ms BIGINT NOT NULL, created_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE provider_webhook_events(
 id UUID PRIMARY KEY, provider VARCHAR(50) NOT NULL, provider_event_id VARCHAR(150) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, received_at TIMESTAMPTZ NOT NULL, processed_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uq_provider_webhook UNIQUE(provider,provider_event_id)
);

CREATE TABLE simulated_provider_transfers(
 id UUID PRIMARY KEY, provider_request_reference VARCHAR(100) NOT NULL UNIQUE,
 provider_transfer_reference VARCHAR(100) NOT NULL UNIQUE, beneficiary_token VARCHAR(100) NOT NULL,
 amount NUMERIC(19,2) NOT NULL, currency VARCHAR(3) NOT NULL, status VARCHAR(20) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT ck_simulator_status CHECK(status IN('PENDING','SUCCESSFUL','FAILED'))
);
