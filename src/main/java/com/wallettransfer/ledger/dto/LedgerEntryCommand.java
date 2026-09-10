package com.wallettransfer.ledger.dto;

import com.wallettransfer.ledger.model.EntryType;
import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryCommand(UUID ledgerAccountId, EntryType type, BigDecimal amount) {}
