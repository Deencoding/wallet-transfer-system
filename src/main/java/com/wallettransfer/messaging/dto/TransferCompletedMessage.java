package com.wallettransfer.messaging.dto;

import java.time.Instant;
import java.util.UUID;

public record TransferCompletedMessage(
        UUID transferId,
        String reference,
        UUID senderWalletId,
        UUID receiverWalletId,
        String amount,
        String currency,
        Instant completedAt) {}
