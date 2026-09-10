package com.wallettransfer.shared.security.model;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {}
