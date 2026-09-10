package com.wallettransfer.authentication.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.security.jwt")
public record JwtProperties(
        String issuer,
        String accessAudience,
        String refreshAudience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String publicKey,
        String privateKey) {}
