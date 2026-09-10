CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
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
