package com.wallettransfer.externaltransfers.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class ExternalTransferNotFoundException extends DomainException {
    public ExternalTransferNotFoundException() {
        super(DomainErrorCode.EXTERNAL_TRANSFER_NOT_FOUND, "External transfer was not found");
    }
}
