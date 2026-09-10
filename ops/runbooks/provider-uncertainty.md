# Provider uncertainty

1. Find the external transfer by internal reference and stable provider request reference.
2. Inspect masked provider interactions and reconciliation attempts.
3. Query provider status; never repeat a potentially successful debit without provider idempotency.
4. Accept signed duplicate webhooks idempotently.
5. Settle only confirmed success and release only confirmed failure.
6. Escalate long-lived unknown outcomes without editing ledger history.
