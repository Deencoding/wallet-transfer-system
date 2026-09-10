package com.wallettransfer.providers.configuration;

import static org.assertj.core.api.Assertions.*;

import com.wallettransfer.providers.client.BankTransferProvider;
import com.wallettransfer.providers.dto.ProviderTransferResult;
import com.wallettransfer.providers.exception.UncertainProviderOutcomeException;
import com.wallettransfer.providers.service.ProviderMetrics;
import com.wallettransfer.providers.service.ResilientProviderTransferService;
import java.math.BigDecimal;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class ResilientProviderTransferServiceTest {
    @Test
    void safeRetriesReuseTheProviderRequestReference() {
        var calls = new AtomicInteger();
        var mismatch = new AtomicBoolean();
        BankTransferProvider provider = new BankTransferProvider() {
            public ProviderTransferResult create(String reference, String token, BigDecimal amount, String currency) {
                if (!reference.equals("PRQ-STABLE")) mismatch.set(true);
                if (calls.incrementAndGet() < 3) throw new IllegalStateException("temporary outage");
                return new ProviderTransferResult(reference, "SIM-1", "SUCCESSFUL", null);
            }

            public ProviderTransferResult queryByRequestReference(String reference) {
                throw new UnsupportedOperationException();
            }
        };
        var config = new ProviderResilienceConfiguration();
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var service = new ResilientProviderTransferService(
                provider,
                config.bankProviderCircuitBreaker(),
                config.bankProviderRetry(),
                new ProviderMetrics(registry));
        assertThat(service.create("PRQ-STABLE", "SIM-SUCCESS", BigDecimal.ONE, "NGN")
                        .status())
                .isEqualTo("SUCCESSFUL");
        assertThat(calls).hasValue(3);
        assertThat(mismatch).isFalse();
    }

    @Test
    void timeoutIsUncertainAndIsNotRetried() {
        var calls = new AtomicInteger();
        BankTransferProvider provider = new BankTransferProvider() {
            public ProviderTransferResult create(String reference, String token, BigDecimal amount, String currency) {
                calls.incrementAndGet();
                throw new com.wallettransfer.providersimulator.exception.SimulatedProviderTimeoutException();
            }

            public ProviderTransferResult queryByRequestReference(String reference) {
                throw new UnsupportedOperationException();
            }
        };
        var config = new ProviderResilienceConfiguration();
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var service = new ResilientProviderTransferService(
                provider,
                config.bankProviderCircuitBreaker(),
                config.bankProviderRetry(),
                new ProviderMetrics(registry));
        assertThatThrownBy(() -> service.create("PRQ-TIMEOUT", "SIM-TIMEOUT", BigDecimal.ONE, "NGN"))
                .isInstanceOf(UncertainProviderOutcomeException.class);
        assertThat(calls).hasValue(1);
    }
}
