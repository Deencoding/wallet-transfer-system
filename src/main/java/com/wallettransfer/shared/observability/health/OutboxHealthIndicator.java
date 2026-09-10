package com.wallettransfer.shared.observability.health;

import com.wallettransfer.shared.observability.service.OperationalMetricsService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("outbox")
public class OutboxHealthIndicator implements HealthIndicator {

    private final OperationalMetricsService metrics;

    public OutboxHealthIndicator(OperationalMetricsService metrics) {
        this.metrics = metrics;
    }

    @Override
    public Health health() {
        var snapshot = metrics.current();
        return Health.up()
                .withDetail("pending", snapshot.pendingOutbox())
                .withDetail("dead", snapshot.deadOutbox())
                .build();
    }
}
