package com.wallettransfer.ledger.dto;

import com.wallettransfer.ledger.model.JournalSourceType;
import com.wallettransfer.shared.money.Currency;
import java.util.List;

public record LedgerPostingCommand(
        JournalSourceType sourceType,
        String sourceReference,
        Currency currency,
        String description,
        List<LedgerEntryCommand> entries) {
    public LedgerPostingCommand {
        entries = List.copyOf(entries);
    }
}
