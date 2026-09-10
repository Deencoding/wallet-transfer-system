package com.wallettransfer.outbox.model;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    FAILED,
    PUBLISHED,
    DEAD
}
