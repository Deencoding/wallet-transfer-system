package com.wallettransfer.authentication.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super(DomainErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
    }
}
