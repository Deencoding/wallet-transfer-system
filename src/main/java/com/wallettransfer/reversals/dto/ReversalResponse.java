package com.wallettransfer.reversals.dto;

import com.wallettransfer.reversals.model.*;
import com.wallettransfer.shared.money.Currency;
import java.math.BigDecimal;
import java.time.Instant;

public record ReversalResponse(
        String reversalReference,
        String originalTransferReference,
        BigDecimal amount,
        Currency currency,
        ReversalStatus status,
        String reason,
        Instant createdAt,
        Instant completedAt) {
    public static ReversalResponse from(TransferReversal value) {
        return new ReversalResponse(
                value.getReference(),
                value.getOriginalTransferReference(),
                value.getAmount(),
                value.getCurrency(),
                value.getStatus(),
                value.getReason(),
                value.getCreatedAt(),
                value.getCompletedAt());
    }
}
