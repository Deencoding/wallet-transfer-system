package com.wallettransfer.shared.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.security.hardening")
public record SecurityHardeningProperties(boolean rateLimitingEnabled, boolean failClosed) {}
