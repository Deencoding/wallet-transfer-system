package com.wallettransfer.wallets.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class WalletTransferRejectedException extends DomainException {
    public WalletTransferRejectedException(DomainErrorCode code, String message) {
        super(code, message);
    }
}
