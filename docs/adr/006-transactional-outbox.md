# ADR-006: Transactional outbox

**Status:** Accepted for the messaging phase

Insert an outbox record in the same PostgreSQL transaction as the business change, then publish it asynchronously to Kafka. Publication is at least once, so consumers must deduplicate durable event identifiers. This closes the database/Kafka dual-write gap but permits duplicate delivery and temporary publication lag; it is not exactly once.
