package com.wallettransfer.authentication.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class RefreshTokenRevokedException extends DomainException {
    public RefreshTokenRevokedException() {
        super(DomainErrorCode.REFRESH_TOKEN_REVOKED, "Refresh token is no longer valid");
    }
}
