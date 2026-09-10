package com.wallettransfer.transfers.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class InvalidTransferStateException extends DomainException {
    public InvalidTransferStateException() {
        super(DomainErrorCode.INVALID_TRANSFER_STATE, "Transfer state transition is invalid");
    }
}
