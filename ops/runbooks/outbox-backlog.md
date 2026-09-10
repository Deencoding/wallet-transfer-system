# Outbox backlog

1. Check Kafka reachability, producer errors, oldest-event age and dead rows.
2. Confirm publisher instances are claiming rows and leases are expiring normally.
3. Restore the dependency; do not manually mark rows published.
4. For dead rows, identify and correct the permanent cause before a controlled replay.
5. Confirm consumer inbox records prevent duplicate notifications and audits.

Never edit event payloads to force publication.
