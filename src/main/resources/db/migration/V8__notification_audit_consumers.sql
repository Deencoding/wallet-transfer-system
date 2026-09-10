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
 CONSTRAINT ck_audit_action CHECK(action IN('TRANSFER_COMPLETED'))
);
CREATE INDEX ix_audit_resource ON audit_records(resource_type,resource_id);
CREATE INDEX ix_audit_occurred ON audit_records(occurred_at DESC);

CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'audit records are immutable'; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER audit_records_immutable BEFORE UPDATE OR DELETE ON audit_records
FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
