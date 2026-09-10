package com.wallettransfer.outbox.exception;

public final class OutboxSerializationException extends RuntimeException {
    public OutboxSerializationException(Throwable cause) {
        super("Could not serialize mandatory outbox event", cause);
    }
}
