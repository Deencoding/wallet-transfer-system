package com.wallettransfer.outbox.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.outbox")
public record OutboxProperties(
        String topic,
        int batchSize,
        Duration pollInterval,
        Duration claimLease,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff) {}
