package com.wallettransfer.wallets.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class WalletNotFoundException extends DomainException {
    public WalletNotFoundException() {
        super(DomainErrorCode.WALLET_NOT_FOUND, "Wallet was not found");
    }
}
