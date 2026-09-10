package com.wallettransfer.externaltransfers.dto;

import com.wallettransfer.externaltransfers.model.*;
import com.wallettransfer.shared.money.Currency;
import java.math.BigDecimal;
import java.time.Instant;

public record ExternalTransferResponse(
        String reference,
        BigDecimal amount,
        Currency currency,
        String beneficiaryToken,
        ExternalTransferStatus status,
        String failureReason,
        Instant createdAt,
        Instant completedAt) {
    public static ExternalTransferResponse from(ExternalTransfer transfer) {
        return new ExternalTransferResponse(
                transfer.getReference(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getBeneficiaryToken(),
                transfer.getStatus(),
                transfer.getFailureReason(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt());
    }
}
