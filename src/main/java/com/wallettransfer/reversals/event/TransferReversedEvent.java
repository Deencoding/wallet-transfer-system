package com.wallettransfer.reversals.event;

import java.time.Instant;
import java.util.UUID;

public record TransferReversedEvent(
        UUID reversalId,
        String reversalReference,
        UUID originalTransferId,
        String originalTransferReference,
        String amount,
        String currency,
        Instant completedAt) {}
