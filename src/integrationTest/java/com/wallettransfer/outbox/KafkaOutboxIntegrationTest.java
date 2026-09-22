package com.wallettransfer.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallettransfer.authentication.service.CustomerRegistrationService;
import com.wallettransfer.ledger.service.LedgerService;
import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.transfers.dto.CreateTransferRequest;
import com.wallettransfer.transfers.service.TransferService;
import com.wallettransfer.wallets.service.WalletService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
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
    private static final DockerImageName IMAGE = DockerImageName.parse("apache/kafka-native:3.8.0");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("outbox_test")
            .withUsername("wallet_test")
            .withPassword("wallet_test");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(IMAGE);

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
    CustomerRegistrationService registration;

    @Autowired
    WalletService wallets;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    TransferService transfers;

    @Autowired
    LedgerService ledgerService;

    @Test
    void internalTransferPublishesNotificationsAndAudit() throws Exception {
        var sender = registration.register("sender-kafka-" + UUID.randomUUID() + "@example.com", "hash");
        var receiver = registration.register("receiver-kafka-" + UUID.randomUUID() + "@example.com", "hash");
        UUID senderWalletId = wallets.getMyWallet(sender.id()).id();
        UUID receiverWalletId = wallets.getMyWallet(receiver.id()).id();
        BigDecimal openingBalance = new BigDecimal("50.00");
        fundWallet(senderWalletId, openingBalance);
        BigDecimal transferAmount = new BigDecimal("15.00");
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(receiverWalletId, transferAmount, Currency.NGN, "Kafka delivery");
        var transfer = transfers.create(sender.id(), transferRequest);
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            try (var connection = dataSource.getConnection();
                    var query = connection.prepareStatement(
                            """
                            SELECT
                              (SELECT count(*) FROM notifications WHERE transfer_id=t.id) notifications,
                              (SELECT count(*) FROM audit_records WHERE resource_id=t.id AND action='TRANSFER_COMPLETED') audits,
                              (SELECT count(*) FROM outbox_events WHERE aggregate_id=t.id AND status='PUBLISHED') published
                            FROM transfers t WHERE reference=?
                            """)) {
                query.setString(1, transfer.reference());
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt("notifications")).isEqualTo(2);
                    assertThat(rows.getInt("audits")).isOne();
                    assertThat(rows.getInt("published")).isOne();
                }
            }
        });
        assertThat(wallets.getMyWallet(sender.id()).availableBalance()).isEqualByComparingTo("35.00");
        assertThat(wallets.getMyWallet(receiver.id()).availableBalance()).isEqualByComparingTo("15.00");
    }

    @Test
    void malformedEventIsRetriedThenPublishedToDeadLetterTopic() {
        var settings = consumerSettings("phase10-dlt-");
        UUID aggregateId = UUID.randomUUID();
        try (var consumer = new KafkaConsumer<String, String>(settings)) {
            List<String> topics = List.of("wallet.transfer.events.v1.dlt");
            consumer.subscribe(topics);
            consumer.poll(Duration.ofMillis(500));
            var record = new ProducerRecord<String, String>("wallet.transfer.events.v1", aggregateId.toString(), "{}");
            record.headers().add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventType", "TransferCompleted".getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
            kafka.send(record);
            Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(
                            consumer.poll(Duration.ofMillis(500)))
                    .anySatisfy(dead -> assertThat(dead.value()).isEqualTo("{}")));
        }
    }

    private Properties consumerSettings(String prefix) {
        var settings = new Properties();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        settings.put(ConsumerConfig.GROUP_ID_CONFIG, prefix + UUID.randomUUID());
        settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return settings;
    }

    private void fundWallet(UUID walletId, BigDecimal amount) throws Exception {
        var walletAccount = ledgerService.getAccountForWallet(walletId);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            UUID journal = UUID.randomUUID(), settlement;
            try (var q = connection.createStatement();
                    var row = q.executeQuery(
                            "SELECT id FROM ledger_accounts WHERE account_code='PLATFORM-NGN-SETTLEMENT'")) {
                row.next();
                settlement = (UUID) row.getObject(1);
            }
            try (var j = connection.prepareStatement(
                    "INSERT INTO journal_transactions(id,reference,source_type,source_reference,currency,posted_at,created_at) VALUES(?,?,'OPENING_BALANCE',?,'NGN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                j.setObject(1, journal);
                j.setString(2, "JRN-" + journal);
                j.setString(3, "FUND-" + journal);
                j.executeUpdate();
            }
            try (var e = connection.prepareStatement(
                    "INSERT INTO journal_entries(id,journal_transaction_id,ledger_account_id,entry_sequence,entry_type,amount,currency,created_at) VALUES(?,?,?,?,?,?, 'NGN',CURRENT_TIMESTAMP)")) {
                e.setObject(1, UUID.randomUUID());
                e.setObject(2, journal);
                e.setObject(3, settlement);
                e.setShort(4, (short) 1);
                e.setString(5, "DEBIT");
                e.setBigDecimal(6, amount);
                e.executeUpdate();
                e.setObject(1, UUID.randomUUID());
                e.setObject(3, walletAccount.getId());
                e.setShort(4, (short) 2);
                e.setString(5, "CREDIT");
                e.executeUpdate();
            }
            try (var w =
                    connection.prepareStatement("UPDATE wallets SET available_balance=?,ledger_balance=? WHERE id=?")) {
                w.setBigDecimal(1, amount);
                w.setBigDecimal(2, amount);
                w.setObject(3, walletId);
                w.executeUpdate();
            }
            connection.commit();
        }
    }
}
