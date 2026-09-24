package com.wallettransfer.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.messaging.dto.TransferCompletedMessage;
import com.wallettransfer.messaging.exception.InvalidEventException;
import com.wallettransfer.messaging.service.TransferEventProcessingService;
import com.wallettransfer.shared.correlation.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "application.messaging.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class TransferCompletedConsumer {

    private final ObjectMapper mapper;
    private final TransferEventProcessingService processing;

    public TransferCompletedConsumer(ObjectMapper mapper, TransferEventProcessingService processing) {
        this.mapper = mapper;
        this.processing = processing;
    }

    @KafkaListener(topics = "${application.outbox.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String correlation = optionalHeader(record, "correlationId");
        if (correlation == null || correlation.isBlank()) {
            correlation = UUID.randomUUID().toString();
        }
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationIdFilter.MDC_KEY, correlation)) {
            String eventIdHeader = header(record, "eventId");
            UUID eventId = UUID.fromString(eventIdHeader);
            String type = header(record, "eventType");
            String versionHeader = header(record, "eventVersion");
            int version = Integer.parseInt(versionHeader);
            var message = mapper.readValue(record.value(), TransferCompletedMessage.class);
            UUID aggregateId = UUID.fromString(record.key());
            processing.process(eventId, type, version, aggregateId, correlation, message);
            acknowledgment.acknowledge();
        } catch (InvalidEventException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidEventException("Kafka transfer event is malformed", exception);
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        String value = optionalHeader(record, name);
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Missing " + name + " header");
        }
        return value;
    }

    private String optionalHeader(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
