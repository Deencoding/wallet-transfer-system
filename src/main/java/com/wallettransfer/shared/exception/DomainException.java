package com.wallettransfer.shared.exception;

public abstract class DomainException extends RuntimeException {

    private final DomainErrorCode code;

    protected DomainException(DomainErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DomainErrorCode code() {
        return code;
    }
}
