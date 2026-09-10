package com.wallettransfer.idempotency.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class IdempotencyKeyConflictException extends DomainException {
    public IdempotencyKeyConflictException() {
        super(DomainErrorCode.IDEMPOTENCY_KEY_CONFLICT, "The idempotency key was already used for a different request");
    }
}
