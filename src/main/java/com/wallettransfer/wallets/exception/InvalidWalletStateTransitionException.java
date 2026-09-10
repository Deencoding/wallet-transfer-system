package com.wallettransfer.wallets.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;
import com.wallettransfer.wallets.model.WalletStatus;

public final class InvalidWalletStateTransitionException extends DomainException {
    public InvalidWalletStateTransitionException(WalletStatus from, WalletStatus to) {
        super(DomainErrorCode.INVALID_WALLET_STATE_TRANSITION, "Wallet cannot transition from " + from + " to " + to);
    }
}
