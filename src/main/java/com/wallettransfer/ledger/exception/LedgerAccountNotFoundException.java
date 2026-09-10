package com.wallettransfer.ledger.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class LedgerAccountNotFoundException extends DomainException {
    public LedgerAccountNotFoundException() {
        super(DomainErrorCode.LEDGER_ACCOUNT_NOT_FOUND, "Ledger account was not found");
    }
}
