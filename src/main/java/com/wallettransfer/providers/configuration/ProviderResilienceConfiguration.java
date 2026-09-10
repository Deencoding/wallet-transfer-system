package com.wallettransfer.providers.configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderResilienceConfiguration {

    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .slowCallRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build());
    private final RetryRegistry retries = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(500, 2, 0.25))
            .retryOnException(error -> !(error
                    instanceof com.wallettransfer.providersimulator.exception.SimulatedProviderTimeoutException))
            .build());

    @Bean
    CircuitBreaker bankProviderCircuitBreaker() {
        return circuitBreakers.circuitBreaker("bankProvider");
    }

    @Bean
    Retry bankProviderRetry() {
        return retries.retry("bankProvider");
    }

    @Bean
    MeterBinder bankProviderCircuitBreakerMetrics() {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakers);
    }

    @Bean
    MeterBinder bankProviderRetryMetrics() {
        return TaggedRetryMetrics.ofRetryRegistry(retries);
    }
}
