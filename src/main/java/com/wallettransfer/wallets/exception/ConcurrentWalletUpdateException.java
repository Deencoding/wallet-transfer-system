package com.wallettransfer.wallets.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class ConcurrentWalletUpdateException extends DomainException {
    public ConcurrentWalletUpdateException() {
        super(DomainErrorCode.CONCURRENT_WALLET_UPDATE, "Wallet was modified concurrently");
    }
}
