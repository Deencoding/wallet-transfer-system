# Transactional outbox

A successful internal transfer inserts one immutable `TransferCompleted` record into PostgreSQL in the same transaction as wallet projections, ledger entries, transfer state, and the idempotency response. Kafka is never called while wallet locks or the financial transaction are open. If mandatory outbox insertion fails, all financial writes roll back.

The scheduled publisher claims ordered batches using `FOR UPDATE SKIP LOCKED`, commits those short claims, and then sends outside the database transaction. Successful sends become `PUBLISHED`; failures use exponential backoff with jitter and become `DEAD` after the configured attempt limit. A lease makes abandoned `PROCESSING` rows claimable again.

The Kafka record key is the transfer UUID. Headers carry `eventId`, `eventType`, `eventVersion`, and the optional correlation ID. Amounts in payloads are decimal strings. No authentication secrets or customer identity data are emitted.

## Delivery guarantee

Delivery is **at least once**. A process can fail after Kafka accepts a record and before PostgreSQL is marked `PUBLISHED`; recovery republishes the same event UUID. Producer idempotence handles protocol-level retries but does not close this database/Kafka acknowledgment window. Every consumer must durably deduplicate `eventId` in the same local transaction as its side effect. Consumer implementation and its dead-letter topic handling belong to Phase 9.

## Operations

Relevant variables are `TRANSFER_EVENTS_TOPIC`, `OUTBOX_BATCH_SIZE`, `OUTBOX_POLL_INTERVAL`, `OUTBOX_CLAIM_LEASE`, `OUTBOX_MAX_ATTEMPTS`, `OUTBOX_INITIAL_BACKOFF`, `OUTBOX_MAX_BACKOFF`, and `OUTBOX_PUBLISHER_ENABLED`. `DEAD` rows require investigation and an intentional replay procedure; editing payloads is prohibited.
