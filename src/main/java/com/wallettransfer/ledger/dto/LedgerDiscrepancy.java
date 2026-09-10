package com.wallettransfer.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerDiscrepancy(
        UUID walletId,
        UUID ledgerAccountId,
        BigDecimal storedBalance,
        BigDecimal calculatedBalance,
        BigDecimal difference,
        Instant detectedAt) {}
