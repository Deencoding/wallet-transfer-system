# Idempotent event consumers

`TransferCompletedConsumer` uses manual Kafka acknowledgment and validates durable event headers plus schema version 1. `TransferEventProcessingService` atomically claims `(consumer_name, event_id)`, creates sender and receiver notification projections, appends an audit record, and marks the inbox row processed in one PostgreSQL transaction.

Kafka delivery remains at least once. If offset acknowledgment is lost after the database commits, redelivery encounters the inbox uniqueness constraint and produces no repeated side effects. Notification and audit uniqueness constraints provide additional defense. No external email or SMS call occurs in this transaction; notifications remain `PENDING` for a later delivery component.

Invalid or repeatedly failing records receive three total delivery attempts with exponential backoff before `DeadLetterPublishingRecoverer` publishes them to `wallet.transfer.events.v1.dlt`. DLT records retain the original payload and Kafka metadata. Operations must inspect and correct the cause before intentional replay.

Audit records are operational evidence rather than the authoritative financial ledger. PostgreSQL rejects updates and deletes; corrections must be represented by new records. Consumer processing can be disabled with `KAFKA_CONSUMER_ENABLED=false` for maintenance, without weakening already committed financial transactions.
