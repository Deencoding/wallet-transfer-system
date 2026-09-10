package com.wallettransfer.transfers.dto;

import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.transfers.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        String reference,
        UUID senderWalletId,
        UUID receiverWalletId,
        BigDecimal amount,
        Currency currency,
        String description,
        TransferStatus status,
        TransferFailureReason failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getReference(),
                transfer.getSenderWalletId(),
                transfer.getReceiverWalletId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getStatus(),
                transfer.getFailureReason(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt(),
                transfer.getCompletedAt());
    }
}
