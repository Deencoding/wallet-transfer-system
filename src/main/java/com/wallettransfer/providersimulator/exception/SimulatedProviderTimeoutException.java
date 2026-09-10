package com.wallettransfer.providersimulator.exception;

public final class SimulatedProviderTimeoutException extends RuntimeException {
    public SimulatedProviderTimeoutException() {
        super("Simulated provider timed out after accepting the request");
    }
}
