# Portfolio overview

This project demonstrates financial correctness beyond CRUD: immutable double-entry accounting, atomic wallet projections, deterministic row locking, durable idempotency, transactional outbox publication, idempotent Kafka consumption, compensating reversals, financial reconciliation, security auditing, metrics, and real-infrastructure tests.

The design deliberately does not claim exactly-once delivery, unlimited hot-wallet throughput, immediate access-token revocation, or production capacity based on a laptop benchmark. PostgreSQL provides atomicity for internal movement; Kafka provides at-least-once delivery; consumers provide idempotent effects.
