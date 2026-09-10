package com.wallettransfer.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.outbox.exception.OutboxSerializationException;
import com.wallettransfer.outbox.model.OutboxEvent;
import com.wallettransfer.outbox.repository.OutboxEventRepository;
import java.time.*;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {
    private final OutboxEventRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public UUID append(String aggregateType, UUID aggregateId, String eventType, int version, Object payload) {
        return appendTo("wallet.transfer.events.v1", aggregateType, aggregateId, eventType, version, payload);
    }

    public UUID appendTo(
            String topic, String aggregateType, UUID aggregateId, String eventType, int version, Object payload) {
        try {
            UUID id = UUID.randomUUID();
            repository.save(new OutboxEvent(
                    id,
                    aggregateType,
                    aggregateId,
                    eventType,
                    version,
                    mapper.writeValueAsString(payload),
                    MDC.get("correlationId"),
                    topic,
                    clock.instant()));
            return id;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new OutboxSerializationException(exception);
        }
    }
}
