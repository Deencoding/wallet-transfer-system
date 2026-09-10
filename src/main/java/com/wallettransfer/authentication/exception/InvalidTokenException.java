package com.wallettransfer.authentication.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class InvalidTokenException extends DomainException {
    public InvalidTokenException() {
        super(DomainErrorCode.INVALID_TOKEN, "Token is invalid or expired");
    }
}
