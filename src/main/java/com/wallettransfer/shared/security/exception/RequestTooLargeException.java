package com.wallettransfer.shared.security.exception;

public final class RequestTooLargeException extends RuntimeException {
    public RequestTooLargeException() {
        super("Request body exceeds the permitted size");
    }
}
