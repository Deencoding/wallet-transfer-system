package com.wallettransfer.ledger.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class UnbalancedJournalException extends DomainException {
    public UnbalancedJournalException() {
        super(DomainErrorCode.UNBALANCED_JOURNAL, "Journal debits and credits must balance");
    }
}
