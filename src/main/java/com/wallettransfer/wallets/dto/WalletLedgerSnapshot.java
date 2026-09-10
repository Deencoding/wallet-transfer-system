package com.wallettransfer.wallets.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletLedgerSnapshot(UUID walletId, BigDecimal ledgerBalance) {}
