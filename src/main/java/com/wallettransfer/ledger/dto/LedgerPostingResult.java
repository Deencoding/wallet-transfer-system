package com.wallettransfer.ledger.dto;

import java.util.UUID;

public record LedgerPostingResult(UUID journalId, String reference) {}
