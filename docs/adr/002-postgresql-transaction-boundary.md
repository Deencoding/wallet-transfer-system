# ADR-002: PostgreSQL financial transaction boundary

**Status:** Accepted

PostgreSQL will atomically persist wallet projections, transfer state, ledger journals, mandatory audit records, idempotent results, and outbox records. Redis and Kafka are outside financial correctness. This gives strong consistency for internal transfers, while a database outage temporarily prevents transfers rather than accepting ambiguous writes.
