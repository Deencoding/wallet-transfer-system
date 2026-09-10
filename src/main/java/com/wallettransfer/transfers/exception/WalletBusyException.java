package com.wallettransfer.transfers.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class WalletBusyException extends DomainException {
    public WalletBusyException() {
        super(DomainErrorCode.WALLET_BUSY, "Wallet is temporarily busy; retry the request");
    }
}
