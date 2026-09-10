package com.wallettransfer.shared.observability.service;

import com.wallettransfer.shared.observability.model.OperationalBacklogSnapshot;
import com.wallettransfer.shared.observability.repository.OperationalMetricsRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OperationalMetricsService {

    private static final Logger log = LoggerFactory.getLogger(OperationalMetricsService.class);
    private final OperationalMetricsRepository repository;
    private final MeterRegistry registry;
    private final AtomicReference<OperationalBacklogSnapshot> snapshot =
            new AtomicReference<>(OperationalBacklogSnapshot.empty());

    public OperationalMetricsService(OperationalMetricsRepository repository, MeterRegistry registry) {
        this.repository = repository;
        this.registry = registry;
    }

    @PostConstruct
    void registerGauges() {
        gauge("wallet.outbox.backlog", "pending", value -> value.pendingOutbox());
        gauge("wallet.outbox.backlog", "processing", value -> value.processingOutbox());
        gauge("wallet.outbox.backlog", "dead", value -> value.deadOutbox());
        Gauge.builder("wallet.outbox.oldest.pending.age", snapshot, state -> state.get()
                        .oldestPendingAgeSeconds())
                .description("Age in seconds of the oldest unpublished outbox event")
                .register(registry);
        Gauge.builder("wallet.reconciliation.open.cases", snapshot, state -> state.get()
                        .openReconciliationCases())
                .tag("severity", "all")
                .register(registry);
        Gauge.builder("wallet.reconciliation.open.cases", snapshot, state -> state.get()
                        .criticalReconciliationCases())
                .tag("severity", "critical")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${application.observability.snapshot-interval:PT15S}")
    public void refresh() {
        try {
            snapshot.set(repository.snapshot());
        } catch (RuntimeException failure) {
            log.warn(
                    "Operational metrics snapshot refresh failed: {}",
                    failure.getClass().getSimpleName());
        }
    }

    public OperationalBacklogSnapshot current() {
        return snapshot.get();
    }

    private void gauge(
            String name, String status, java.util.function.ToDoubleFunction<OperationalBacklogSnapshot> value) {
        Gauge.builder(name, snapshot, state -> value.applyAsDouble(state.get()))
                .tag("status", status)
                .register(registry);
    }
}
