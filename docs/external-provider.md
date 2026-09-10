# External provider simulator

External transfers reserve available funds in PostgreSQL and insert `ExternalTransferRequested` into the transactional outbox. The request returns `202 Accepted`; provider work occurs later from `wallet.external-transfer.requests.v1`, outside wallet locks and the financial transaction.

While reserved, available balance is reduced but ledger balance is unchanged. Provider success settles the reservation, reduces ledger balance, and posts a balanced journal from the customer liability account to `PLATFORM-NGN-EXTERNAL-SETTLEMENT`. Failure releases available funds and posts no journal. Pending or timed-out requests retain the reservation as `PENDING_PROVIDER_CONFIRMATION`.

The simulator accepts only fake tokens: `SIM-SUCCESS`, `SIM-FAIL`, `SIM-PENDING`, `SIM-TIMEOUT`, and `SIM-TIMEOUT-SUCCESS`. Its provider request reference is unique and replay-safe. `SIM-TIMEOUT-SUCCESS` intentionally commits success before throwing a timeout to demonstrate why an uncertain operation must not be blindly retried.

Webhooks use `HMAC-SHA256(secret, timestamp + "." + rawBody)`, a five-minute timestamp tolerance, constant-time comparison, and unique `(provider, provider_event_id)` storage. The webhook secret is supplied through `PROVIDER_WEBHOOK_SECRET`. Request/response interaction records contain masked simulator tokens and never store signatures or authorization secrets.

Phase 10 does not automatically retry uncertain provider operations. Status-query reconciliation, circuit breaking, and carefully bounded retries belong to Phase 11.
