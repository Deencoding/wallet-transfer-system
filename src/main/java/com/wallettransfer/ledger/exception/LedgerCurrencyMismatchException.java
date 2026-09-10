package com.wallettransfer.ledger.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class LedgerCurrencyMismatchException extends DomainException {
    public LedgerCurrencyMismatchException() {
        super(DomainErrorCode.LEDGER_CURRENCY_MISMATCH, "Ledger currencies must match");
    }
}
