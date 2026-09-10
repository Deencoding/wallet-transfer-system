package com.wallettransfer.providers.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.provider")
public record ProviderProperties(boolean simulatorEnabled, String webhookSecret, Duration webhookTolerance) {
    private static final int MINIMUM_SECRET_LENGTH = 32;

    public ProviderProperties {
        if (webhookSecret == null || webhookSecret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalArgumentException("Provider webhook secret must contain at least 32 characters");
        }
    }
}
