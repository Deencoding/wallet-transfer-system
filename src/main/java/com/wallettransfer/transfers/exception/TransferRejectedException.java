package com.wallettransfer.transfers.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class TransferRejectedException extends DomainException {
    public TransferRejectedException(DomainErrorCode code, String message) {
        super(code, message);
    }
}
