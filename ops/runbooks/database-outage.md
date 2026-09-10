# PostgreSQL outage

1. Stop financial traffic through readiness-aware routing.
2. Restore the primary database and verify Flyway/schema compatibility before accepting traffic.
3. Check failed client requests by correlation ID; do not assume an outcome without querying the transfer/idempotency record.
4. Run financial reconciliation and the invariant verifier.
5. Allow outbox publishing to catch up.

The service must never acknowledge an uncommitted transfer. Clients retry mutations with the original idempotency key.
