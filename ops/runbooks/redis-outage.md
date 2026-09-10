# Redis outage

Production rate limiting fails closed. Sensitive POST endpoints can return `429` while read operations and committed financial data remain safe.

1. Confirm Redis health, memory and persistence state.
2. Restore Redis before reopening sensitive traffic.
3. Do not switch to fail-open during an active abuse event.
4. Verify rate-limit responses and readiness after recovery.

Redis contains no authoritative wallet locks or balances.
