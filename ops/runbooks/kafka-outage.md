# Kafka outage

1. Confirm PostgreSQL and the application remain ready except for the Kafka component.
2. Inspect `wallet_outbox_backlog` and oldest pending age; do not replay business transfers.
3. Restore broker connectivity and verify producer acknowledgements.
4. Confirm backlog drains and `DEAD` rows do not increase.
5. Verify consumers progress; duplicates are acceptable only when inbox deduplication suppresses repeated effects.

Funds are moved in PostgreSQL, not Kafka. Never compensate a successful transfer merely because its notification is delayed.
