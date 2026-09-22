package com.wallettransfer.transfers.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransferMetrics {

    private final MeterRegistry registry;

    public TransferMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void successfulAfterCommit(Timer.Sample sample) {
        Runnable record = () -> record(sample, "successful");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronization synchronization = new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record.run();
                }
            };
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        } else {
            record.run();
        }
    }

    public void failed(Timer.Sample sample) {
        record(sample, "failed");
    }

    private void record(Timer.Sample sample, String outcome) {
        registry.counter("wallet.transfers", "type", "internal", "outcome", outcome)
                .increment();
        Timer timer = registry.timer("wallet.transfer.duration", "type", "internal", "outcome", outcome);
        sample.stop(timer);
    }
}
