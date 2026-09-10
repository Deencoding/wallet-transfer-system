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
