package com.wallettransfer.providers.exception;

public final class UncertainProviderOutcomeException extends RuntimeException {
    public UncertainProviderOutcomeException(Throwable cause) {
        super("Provider outcome is uncertain", cause);
    }
}
