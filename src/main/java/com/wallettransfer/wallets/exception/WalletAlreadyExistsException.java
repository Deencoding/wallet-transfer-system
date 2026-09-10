package com.wallettransfer.wallets.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class WalletAlreadyExistsException extends DomainException {
    public WalletAlreadyExistsException() {
        super(DomainErrorCode.WALLET_ALREADY_EXISTS, "Wallet already exists");
    }
}
