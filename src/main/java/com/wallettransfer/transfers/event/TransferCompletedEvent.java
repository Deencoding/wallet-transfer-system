package com.wallettransfer.transfers.event;

import java.time.Instant;
import java.util.UUID;

public record TransferCompletedEvent(
        UUID transferId,
        String reference,
        UUID senderWalletId,
        UUID receiverWalletId,
        String amount,
        String currency,
        Instant completedAt) {}
