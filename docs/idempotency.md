# Transfer idempotency

`POST /api/v1/transfers` requires `Idempotency-Key`. PostgreSQL uniquely scopes a key by authenticated user and endpoint. A SHA-256 fingerprint covers receiver, normalized amount, currency, and description. The idempotency claim, wallet movement, transfer, ledger journal, and stored response commit in one transaction.

PostgreSQL `INSERT ... ON CONFLICT DO NOTHING` arbitrates concurrent claims. Identical retries deserialize the stored response without invoking the transfer workflow; a different fingerprint returns `IDEMPOTENCY_KEY_CONFLICT`. Technical failures roll back the claim together with all financial writes. Records carry a 24-hour minimum retention timestamp. Redis is not required for correctness, and the system makes no exactly-once delivery claim.
