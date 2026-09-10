package com.wallettransfer.providers.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ProviderMetrics {

    private final MeterRegistry registry;

    public ProviderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void record(Timer.Sample sample, String operation, String outcome) {
        registry.counter(
                        "wallet.provider.requests", "provider", "simulator", "operation", operation, "outcome", outcome)
                .increment();
        sample.stop(registry.timer(
                "wallet.provider.request.duration",
                "provider",
                "simulator",
                "operation",
                operation,
                "outcome",
                outcome));
    }
}
