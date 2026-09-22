package com.wallettransfer.outbox.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {
    @Test
    void transitionsThroughClaimFailureAndPublication() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var retryAt = now.plusSeconds(1);
        var event = new OutboxEvent(
                UUID.randomUUID(), "TRANSFER", UUID.randomUUID(), "TransferCompleted", 1, "{}", null, now);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        event.claim(now);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        event.failed(now, retryAt, "offline", 10);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttemptCount()).isOne();
        event.claim(now);
        event.published(now);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void exhaustedRetriesBecomeDead() {
        Instant now = Instant.now();
        var event = new OutboxEvent(
                UUID.randomUUID(), "TRANSFER", UUID.randomUUID(), "TransferCompleted", 1, "{}", null, now);
        event.claim(now);
        event.failed(now, now, "offline", 1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
    }
}
