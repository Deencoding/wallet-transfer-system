package com.wallettransfer.externaltransfers.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class ExternalIdempotencyConflictException extends DomainException {
    public ExternalIdempotencyConflictException() {
        super(DomainErrorCode.IDEMPOTENCY_KEY_CONFLICT, "Idempotency key was already used with another request");
    }
}
