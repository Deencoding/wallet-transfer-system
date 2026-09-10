package com.wallettransfer.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wallettransfer.shared.security.config.SecurityHardeningProperties;
import com.wallettransfer.shared.security.model.RateLimitPolicy;
import com.wallettransfer.shared.security.repository.RedisRateLimitRepository;
import com.wallettransfer.shared.security.service.RateLimitService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    private final RateLimitPolicy policy = new RateLimitPolicy("login", 10, Duration.ofMinutes(5));

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        RedisRateLimitRepository repository = mock(RedisRateLimitRepository.class);
        when(repository.consume("key", policy)).thenThrow(new IllegalStateException("unavailable"));
        var service = new RateLimitService(repository, new SecurityHardeningProperties(true, true));

        assertThat(service.consume("key", policy).allowed()).isFalse();
    }

    @Test
    void canFailOpenOnlyWhenExplicitlyConfigured() {
        RedisRateLimitRepository repository = mock(RedisRateLimitRepository.class);
        when(repository.consume("key", policy)).thenThrow(new IllegalStateException("unavailable"));
        var service = new RateLimitService(repository, new SecurityHardeningProperties(true, false));

        assertThat(service.consume("key", policy).allowed()).isTrue();
    }
}
