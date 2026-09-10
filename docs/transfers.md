# Atomic internal transfers

`TransferController` derives the sender from the JWT; clients provide only the receiver wallet, amount, currency, and description. `TransferService` owns one PostgreSQL transaction that creates the transfer, moves both wallet projections, posts the sender debit and receiver credit, and marks the transfer successful. A mandatory ledger or database failure rolls all of it back.

The sender must be active, the receiver must not be closed, both wallets and the request must use NGN, the amount must be positive scale-2, sender and receiver must differ, and available funds must be sufficient. Frozen wallets may receive but cannot send.

Successful accounting is:

```text
DEBIT  sender wallet liability account
CREDIT receiver wallet liability account
```

Test opening funds are posted against the seeded `PLATFORM-NGN-SETTLEMENT` asset account and accompanied by the wallet projection update. There is no public funding or arbitrary adjustment endpoint.

Phase 5 guarantees transaction atomicity for a request, but does not yet guarantee safe simultaneous debits from one wallet. PostgreSQL sender-row locking and concurrency stress tests are Phase 6. Durable HTTP idempotency is Phase 7.
