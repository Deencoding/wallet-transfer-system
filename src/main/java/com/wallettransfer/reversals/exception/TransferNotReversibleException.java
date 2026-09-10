package com.wallettransfer.reversals.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class TransferNotReversibleException extends DomainException {
    public TransferNotReversibleException() {
        super(DomainErrorCode.TRANSFER_NOT_REVERSIBLE, "Only a successful unreversed transfer can be reversed");
    }
}
