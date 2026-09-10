package com.wallettransfer.wallets.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class WalletHasBalanceException extends DomainException {
    public WalletHasBalanceException() {
        super(DomainErrorCode.WALLET_HAS_BALANCE, "A wallet with a balance cannot be closed");
    }
}
