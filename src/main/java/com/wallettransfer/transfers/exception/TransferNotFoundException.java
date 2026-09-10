package com.wallettransfer.transfers.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class TransferNotFoundException extends DomainException {
    public TransferNotFoundException() {
        super(DomainErrorCode.TRANSFER_NOT_FOUND, "Transfer was not found");
    }
}
