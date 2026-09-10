package com.wallettransfer.shared.security.service;

import com.wallettransfer.shared.security.config.SecurityHardeningProperties;
import com.wallettransfer.shared.security.model.RateLimitDecision;
import com.wallettransfer.shared.security.model.RateLimitPolicy;
import com.wallettransfer.shared.security.repository.RedisRateLimitRepository;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private final RedisRateLimitRepository repository;
    private final SecurityHardeningProperties properties;

    public RateLimitService(RedisRateLimitRepository repository, SecurityHardeningProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public RateLimitDecision consume(String key, RateLimitPolicy policy) {
        if (!properties.rateLimitingEnabled()) {
            return new RateLimitDecision(true, 0);
        }
        try {
            return repository.consume(key, policy);
        } catch (RuntimeException unavailable) {
            if (properties.failClosed()) {
                return new RateLimitDecision(false, 5);
            }
            return new RateLimitDecision(true, 0);
        }
    }
}
