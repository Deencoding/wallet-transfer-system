package com.wallettransfer.outbox.service;

import com.wallettransfer.outbox.model.OutboxEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "application.outbox.publisher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxClaimService claims;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry metrics;

    public OutboxPublisher(
            OutboxClaimService claims,
            KafkaTemplate<String, String> kafka,
            MeterRegistry metrics) {
        this.claims = claims;
        this.kafka = kafka;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${application.outbox.poll-interval:PT1S}")
    public void publishPending() {
        for (OutboxEvent event : claims.claim()) publish(event);
    }

    private void publish(OutboxEvent event) {
        Timer.Sample sample = Timer.start(metrics);
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    event.getDestinationTopic(), event.getAggregateId().toString(), event.getPayload());
            byte[] eventIdHeader = event.getId().toString().getBytes(StandardCharsets.UTF_8);
            record.headers().add("eventId", eventIdHeader);
            byte[] eventTypeHeader = event.getEventType().getBytes(StandardCharsets.UTF_8);
            record.headers().add("eventType", eventTypeHeader);
            byte[] versionHeader = Integer.toString(event.getEventVersion()).getBytes(StandardCharsets.UTF_8);
            record.headers().add("eventVersion", versionHeader);
            if (event.getCorrelationId() != null) {
                byte[] correlationHeader = event.getCorrelationId().getBytes(StandardCharsets.UTF_8);
                record.headers().add("correlationId", correlationHeader);
            }
            kafka.send(record).get(10, TimeUnit.SECONDS);
            claims.published(event.getId());
            record(sample, "published");
        } catch (Exception failure) {
            claims.failed(event.getId(), failure);
            record(sample, "failed");
        }
    }

    private void record(Timer.Sample sample, String outcome) {
        metrics.counter("wallet.outbox.events", "outcome", outcome).increment();
        Timer timer = metrics.timer("wallet.outbox.publish.duration", "outcome", outcome);
        sample.stop(timer);
    }
}
