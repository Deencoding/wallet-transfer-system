package com.wallettransfer.reversals.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class ReversalIdempotencyConflictException extends DomainException {
    public ReversalIdempotencyConflictException() {
        super(
                DomainErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                "Idempotency key was already used with another reversal request");
    }
}
