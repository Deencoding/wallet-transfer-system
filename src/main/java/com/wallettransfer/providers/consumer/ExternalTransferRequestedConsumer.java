package com.wallettransfer.providers.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.externaltransfers.event.ExternalTransferRequestedEvent;
import com.wallettransfer.externaltransfers.service.ExternalTransferCompletionService;
import com.wallettransfer.providers.exception.UncertainProviderOutcomeException;
import com.wallettransfer.providers.service.*;
import java.time.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "application.messaging.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class ExternalTransferRequestedConsumer {
    private final ObjectMapper mapper;
    private final ResilientProviderTransferService provider;
    private final ExternalTransferCompletionService completion;
    private final ProviderInteractionService interactions;
    private final Clock clock;

    public ExternalTransferRequestedConsumer(
            ObjectMapper mapper,
            ResilientProviderTransferService provider,
            ExternalTransferCompletionService completion,
            ProviderInteractionService interactions,
            Clock clock) {
        this.mapper = mapper;
        this.provider = provider;
        this.completion = completion;
        this.interactions = interactions;
        this.clock = clock;
    }

    @KafkaListener(topics = "wallet.external-transfer.requests.v1", groupId = "external-transfer-provider-v1")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        var event = mapper.readValue(record.value(), ExternalTransferRequestedEvent.class);
        completion.start(event.transferId());
        Instant start = clock.instant();
        try {
            var response = provider.create(
                    event.providerRequestReference(),
                    event.beneficiaryToken(),
                    new java.math.BigDecimal(event.amount()),
                    event.currency());
            interactions.record(
                    event.transferId(),
                    event.providerRequestReference(),
                    event.beneficiaryToken(),
                    response.status(),
                    200,
                    "status=" + response.status(),
                    Duration.between(start, clock.instant()).toMillis(),
                    start,
                    clock.instant());
            if (response.status().equals("SUCCESSFUL")) {
                completion.successful(event.transferId(), response.providerReference());
            } else if (response.status().equals("FAILED")) {
                completion.failed(event.transferId(), response.failureReason());
            } else {
                completion.uncertain(event.transferId(), "Provider returned pending");
            }
            ack.acknowledge();
        } catch (UncertainProviderOutcomeException timeout) {
            interactions.record(
                    event.transferId(),
                    event.providerRequestReference(),
                    event.beneficiaryToken(),
                    "TIMEOUT",
                    null,
                    "timeout",
                    Duration.between(start, clock.instant()).toMillis(),
                    start,
                    clock.instant());
            completion.uncertain(event.transferId(), timeout.getMessage());
            ack.acknowledge();
        }
    }
}
