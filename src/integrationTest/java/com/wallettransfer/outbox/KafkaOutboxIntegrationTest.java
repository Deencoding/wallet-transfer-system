package com.wallettransfer.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {"application.outbox.poll-interval=PT0.1S", "application.outbox.initial-backoff=PT0.1S"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaOutboxIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("outbox_test")
            .withUsername("wallet_test")
            .withPassword("wallet_test");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    DataSource dataSource;

    @Autowired
    com.wallettransfer.authentication.service.CustomerRegistrationService registration;

    @Autowired
    com.wallettransfer.wallets.service.WalletService wallets;

    @Autowired
    com.wallettransfer.externaltransfers.service.ExternalTransferService externalTransfers;

    @Autowired
    org.springframework.kafka.core.KafkaTemplate<String, String> kafka;

    @Test
    void externalTransferOutboxEventIsProcessedBySimulator() throws Exception {
        var owner = registration.register("external-kafka-" + UUID.randomUUID() + "@example.com", "hash");
        UUID walletId = wallets.getMyWallet(owner.id()).id();
        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        "UPDATE wallets SET available_balance=50.00,ledger_balance=50.00 WHERE id=?")) {
            update.setObject(1, walletId);
            update.executeUpdate();
        }
        var accepted = externalTransfers.create(
                owner.id(),
                "external-kafka-" + UUID.randomUUID(),
                new com.wallettransfer.externaltransfers.dto.CreateExternalTransferRequest(
                        new java.math.BigDecimal("15.00"),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "SIM-SUCCESS",
                        "Kafka provider test"));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            try (var connection = dataSource.getConnection();
                    var q = connection.prepareStatement(
                            "SELECT e.status,w.available_balance,w.ledger_balance,(SELECT count(*) FROM provider_interactions i WHERE i.external_transfer_id=e.id) FROM external_transfers e JOIN wallets w ON w.id=e.sender_wallet_id WHERE e.reference=?")) {
                q.setString(1, accepted.reference());
                try (var rows = q.executeQuery()) {
                    rows.next();
                    assertThat(rows.getString(1)).isEqualTo("SUCCESSFUL");
                    assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("35.00");
                    assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("35.00");
                    assertThat(rows.getInt(4)).isOne();
                }
            }
        });
    }

    @Test
    void malformedEventIsRetriedThenPublishedToDeadLetterTopic() {
        var settings = consumerSettings("phase10-dlt-");
        UUID aggregateId = UUID.randomUUID();
        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(settings)) {
            consumer.subscribe(java.util.List.of("wallet.transfer.events.v1.dlt"));
            consumer.poll(Duration.ofMillis(500));
            var record = new org.apache.kafka.clients.producer.ProducerRecord<String, String>(
                    "wallet.transfer.events.v1", aggregateId.toString(), "{}");
            record.headers()
                    .add("eventId", UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            record.headers().add("eventType", "TransferCompleted".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            record.headers().add("eventVersion", "1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            kafka.send(record);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(
                            consumer.poll(Duration.ofMillis(500)))
                    .anySatisfy(dead -> assertThat(dead.value()).isEqualTo("{}")));
        }
    }

    private java.util.Properties consumerSettings(String prefix) {
        var settings = new java.util.Properties();
        settings.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        settings.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, prefix + UUID.randomUUID());
        settings.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        settings.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class);
        settings.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class);
        return settings;
    }
}
