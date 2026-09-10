package com.wallettransfer.idempotency.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class InvalidIdempotencyKeyException extends DomainException {
    public InvalidIdempotencyKeyException() {
        super(DomainErrorCode.INVALID_IDEMPOTENCY_KEY, "Idempotency-Key must contain 8 to 255 valid characters");
    }
}
