package com.wallettransfer.wallets.dto;

import com.wallettransfer.shared.money.Currency;
import java.util.UUID;

public record WalletMovement(UUID senderWalletId, UUID receiverWalletId, Currency currency) {}
