package com.wallettransfer.shared.security.model;

import java.time.Duration;

public record RateLimitPolicy(String name, long requests, Duration window) {}
