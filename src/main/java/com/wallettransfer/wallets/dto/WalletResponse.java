package com.wallettransfer.wallets.dto;

import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.wallets.model.Wallet;
import com.wallettransfer.wallets.model.WalletStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        UUID ownerId,
        Currency currency,
        WalletStatus status,
        BigDecimal availableBalance,
        BigDecimal ledgerBalance,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getOwnerId(),
                wallet.getCurrency(),
                wallet.getStatus(),
                wallet.getAvailableBalance(),
                wallet.getLedgerBalance(),
                wallet.getVersion(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt());
    }
}
