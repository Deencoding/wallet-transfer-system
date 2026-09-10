package com.wallettransfer.messaging.configuration;

import com.wallettransfer.outbox.configuration.OutboxProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfiguration {
    @Bean
    NewTopic transferEvents(OutboxProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic transferEventsDlt(OutboxProperties properties) {
        return TopicBuilder.name(properties.topic() + ".dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic externalTransferRequests() {
        return TopicBuilder.name("wallet.external-transfer.requests.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic externalTransferRequestsDlt() {
        return TopicBuilder.name("wallet.external-transfer.requests.v1.dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
