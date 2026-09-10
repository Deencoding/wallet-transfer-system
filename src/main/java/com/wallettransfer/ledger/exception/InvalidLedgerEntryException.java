package com.wallettransfer.ledger.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class InvalidLedgerEntryException extends DomainException {
    public InvalidLedgerEntryException(String message) {
        super(DomainErrorCode.INVALID_LEDGER_ENTRY, message);
    }
}
