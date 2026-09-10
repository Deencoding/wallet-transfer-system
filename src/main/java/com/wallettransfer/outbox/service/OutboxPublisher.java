package com.wallettransfer.outbox.service;

import com.wallettransfer.outbox.configuration.OutboxProperties;
import com.wallettransfer.outbox.model.OutboxEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final OutboxProperties properties;
    private final MeterRegistry metrics;

    public OutboxPublisher(
            OutboxClaimService claims,
            KafkaTemplate<String, String> kafka,
            OutboxProperties properties,
            MeterRegistry metrics) {
        this.claims = claims;
        this.kafka = kafka;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${application.outbox.poll-interval:PT1S}")
    public void publishPending() {
        for (OutboxEvent event : claims.claim()) publish(event);
    }

    private void publish(OutboxEvent event) {
        Timer.Sample sample = Timer.start(metrics);
        try {
            var record = new ProducerRecord<String, String>(
                    event.getDestinationTopic(), event.getAggregateId().toString(), event.getPayload());
            record.headers().add("eventId", event.getId().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            record.headers().add("eventType", event.getEventType().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            record.headers()
                    .add(
                            "eventVersion",
                            Integer.toString(event.getEventVersion())
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (event.getCorrelationId() != null)
                record.headers()
                        .add(
                                "correlationId",
                                event.getCorrelationId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
        sample.stop(metrics.timer("wallet.outbox.publish.duration", "outcome", outcome));
    }
}
