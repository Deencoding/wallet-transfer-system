package com.wallettransfer.users.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class EmailAlreadyRegisteredException extends DomainException {
    public EmailAlreadyRegisteredException() {
        super(DomainErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
    }
}
