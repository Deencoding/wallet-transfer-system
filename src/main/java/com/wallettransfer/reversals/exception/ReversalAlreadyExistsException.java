package com.wallettransfer.reversals.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class ReversalAlreadyExistsException extends DomainException {
    public ReversalAlreadyExistsException() {
        super(DomainErrorCode.REVERSAL_ALREADY_EXISTS, "The transfer already has a reversal");
    }
}
