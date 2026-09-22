# Transfer state machine

The planned internal-transfer transitions are:

```text
PENDING -> PROCESSING
PENDING -> FAILED
PROCESSING -> SUCCESSFUL
PROCESSING -> FAILED
SUCCESSFUL -> REVERSED
```

`FAILED` and `REVERSED` are terminal. A successful transfer may be reversed once by compensating entries; its original ledger records remain unchanged.
