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
