package com.wallettransfer.authentication.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class AuthenticationFailedException extends DomainException {
    public AuthenticationFailedException() {
        super(DomainErrorCode.AUTHENTICATION_FAILED, "Authentication failed");
    }
}
