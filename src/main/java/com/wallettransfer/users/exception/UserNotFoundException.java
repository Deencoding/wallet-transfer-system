package com.wallettransfer.users.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class UserNotFoundException extends DomainException {
    public UserNotFoundException() {
        super(DomainErrorCode.USER_NOT_FOUND, "User was not found");
    }
}
