package com.wallettransfer.outbox.service;

import com.wallettransfer.outbox.configuration.OutboxProperties;
import com.wallettransfer.outbox.model.OutboxEvent;
import com.wallettransfer.outbox.repository.OutboxEventRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaimService {
    private final OutboxEventRepository repository;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxClaimService(OutboxEventRepository repository, OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<OutboxEvent> claim() {
        Instant now = clock.instant();
        var events = repository.findClaimable(now, now.minus(properties.claimLease()), properties.batchSize());
        events.forEach(event -> event.claim(now));
        return List.copyOf(events);
    }

    @Transactional
    public void published(UUID id) {
        repository.findById(id).orElseThrow().published(clock.instant());
    }

    @Transactional
    public void failed(UUID id, Throwable failure) {
        var event = repository.findById(id).orElseThrow();
        long exponential = Math.min(
                properties.maxBackoff().toMillis(),
                properties.initialBackoff().toMillis() * (1L << Math.min(event.getAttemptCount(), 20)));
        long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(Math.max(1, exponential / 4));
        event.failed(
                clock.instant(),
                clock.instant().plusMillis(exponential + jitter),
                failure.getMessage(),
                properties.maxAttempts());
    }
}
