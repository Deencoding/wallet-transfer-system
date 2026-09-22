package com.wallettransfer.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.authentication.service.CustomerRegistrationService;
import com.wallettransfer.idempotency.service.IdempotencyService;
import com.wallettransfer.ledger.dto.LedgerEntryCommand;
import com.wallettransfer.ledger.dto.LedgerPostingCommand;
import com.wallettransfer.ledger.exception.UnbalancedJournalException;
import com.wallettransfer.ledger.model.EntryType;
import com.wallettransfer.ledger.model.JournalSourceType;
import com.wallettransfer.ledger.service.LedgerReconciliationService;
import com.wallettransfer.ledger.service.LedgerService;
import com.wallettransfer.messaging.dto.TransferCompletedMessage;
import com.wallettransfer.messaging.service.TransferEventProcessingService;
import com.wallettransfer.reconciliation.dto.RepairWalletProjectionRequest;
import com.wallettransfer.reconciliation.exception.ReconciliationRepairConflictException;
import com.wallettransfer.reconciliation.service.FinancialReconciliationService;
import com.wallettransfer.reconciliation.service.ReconciliationRepairService;
import com.wallettransfer.reversals.dto.CreateReversalRequest;
import com.wallettransfer.reversals.exception.ReversalAlreadyExistsException;
import com.wallettransfer.reversals.exception.ReversalIdempotencyConflictException;
import com.wallettransfer.reversals.service.TransferReversalService;
import com.wallettransfer.shared.exception.DomainException;
import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.shared.observability.service.OperationalMetricsService;
import com.wallettransfer.transfers.dto.CreateTransferRequest;
import com.wallettransfer.transfers.service.TransferService;
import com.wallettransfer.wallets.exception.WalletTransferRejectedException;
import com.wallettransfer.wallets.repository.WalletRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.flywaydb.core.Flyway;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        properties = {"application.outbox.publisher-enabled=false", "application.messaging.consumer-enabled=false"})
@AutoConfigureMockMvc
class PostgresMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("wallet_test")
            .withUsername("wallet_test")
            .withPassword("wallet_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    DataSource dataSource;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CustomerRegistrationService registration;

    @Autowired
    LedgerService ledgerService;

    @Autowired
    LedgerReconciliationService reconciliationService;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    TransferService transferService;

    @Autowired
    IdempotencyService idempotencyService;

    @Autowired
    TransferEventProcessingService transferEventProcessingService;

    @Autowired
    FinancialReconciliationService financialReconciliationService;

    @Autowired
    ReconciliationRepairService reconciliationRepairService;

    @Autowired
    TransferReversalService transferReversalService;

    @Autowired
    OperationalMetricsService operationalMetricsService;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;

    @Test
    void onlyInternalTransferRoutesAreRegistered() {
        var paths = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .toList();
        assertThat(paths).contains("/api/v1/transfers", "/api/v1/transfers/{reference}");
        assertThat(paths)
                .noneMatch(path -> path.contains("external-transfers")
                        || path.contains("/webhooks/providers")
                        || path.startsWith("/simulator"));
    }

    @Test
    void appliesTheFoundationMigration() throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT description FROM schema_metadata WHERE id = 1");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("description")).isEqualTo("Wallet transfer system schema initialized");
        }
    }

    @Test
    void freshSchemaContainsOnlyCurrentTablesAndMigrationIsRepeatable() throws Exception {
        String schema = "fresh_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .load();
        assertThat(flyway.migrate().migrationsExecuted).isOne();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = ?")) {
            statement.setString(1, schema);
            try (var rows = statement.executeQuery()) {
                var tables = new java.util.ArrayList<String>();
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
                assertThat(tables)
                        .containsExactlyInAnyOrder(
                                "flyway_schema_history",
                                "schema_metadata",
                                "users",
                                "roles",
                                "user_roles",
                                "refresh_tokens",
                                "wallets",
                                "ledger_accounts",
                                "journal_transactions",
                                "journal_entries",
                                "transfers",
                                "idempotency_records",
                                "outbox_events",
                                "consumed_events",
                                "notifications",
                                "audit_records",
                                "transfer_reversals",
                                "reconciliation_runs",
                                "reconciliation_cases",
                                "reconciliation_repairs",
                                "security_events");
            }
        }
    }

    @Test
    void registrationLoginProfileRotationReuseAndLogoutWorkEndToEnd() throws Exception {
        String email = "phase2-" + UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.roles[0]").value("CUSTOMER"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong password value\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Email or password is incorrect"));

        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement("SELECT principal_hash, metadata::text FROM security_events "
                        + "WHERE event_type='AUTHENTICATION' AND outcome='FAILURE' ORDER BY occurred_at DESC LIMIT 1");
                var rows = query.executeQuery()) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).hasSize(64).doesNotContain(email);
            assertThat(rows.getString(2)).doesNotContain(email).doesNotContain("wrong password value");
        }

        String loginBody = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode login = mapper.readTree(loginBody);
        String access = login.get("accessToken").asText();
        String originalRefresh = login.get("refreshToken").asText();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value(email));

        String rotatedBody = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + originalRefresh + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String rotatedRefresh = mapper.readTree(rotatedBody).get("refreshToken").asText();

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + originalRefresh + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        "UPDATE security_events SET outcome='SUCCESS' WHERE event_type='AUTHENTICATION'")) {
            assertThatThrownBy(update::executeUpdate).isInstanceOf(SQLException.class);
        }

        String secondLoginBody = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode secondLogin = mapper.readTree(secondLoginBody);
        String secondAccess = secondLogin.get("accessToken").asText();
        String secondRefresh = secondLogin.get("refreshToken").asText();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + secondAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void concurrentRefreshRequestsCannotCreateTwoValidTokenChains() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String refresh = mapper.readTree(body).get("refreshToken").asText();
        String payload = "{\"refreshToken\":\"" + refresh + "\"}";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = IntStream.range(0, 2)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }))
                    .toList();
            start.countDown();
            List<Integer> statuses = results.stream()
                    .map(result -> {
                        try {
                            return result.get(10, TimeUnit.SECONDS);
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .toList();
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void httpSecurityRejectsAmbiguousAndOversizedRequestsAndAddsDefensiveHeaders() throws Exception {
        String validPayload = "{\"email\":\"secure-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"correct horse battery staple\"}";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(MockMvcResultMatchers.header().string("X-Frame-Options", "DENY"))
                .andExpect(MockMvcResultMatchers.header().exists("Content-Security-Policy"));

        mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"nobody@example.com\",\"password\":\"long enough password\",\"admin\":true}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(70_000)))
                .andExpect(MockMvcResultMatchers.status().isPayloadTooLarge())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void databaseEnforcesEmailUniqueness() throws Exception {
        try (var connection = dataSource.getConnection()) {
            UUID first = UUID.randomUUID();
            String email = first + "@example.com";
            try (var statement = connection.prepareStatement(
                    """
                    INSERT INTO users(id,email,password_hash,status,created_at,updated_at)
                    VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                statement.setObject(1, first);
                statement.setString(2, email);
                statement.setString(3, "a-valid-placeholder-hash");
                statement.executeUpdate();
                statement.setObject(1, UUID.randomUUID());
                assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void registrationCreatesExactlyOneWalletAndCustomerCanRetrieveIt() throws Exception {
        String email = "wallet-" + UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        String loginBody = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String access = mapper.readTree(loginBody).get("accessToken").asText();
        String walletBody = mockMvc.perform(
                        MockMvcRequestBuilders.get("/api/v1/wallets/me").header("Authorization", "Bearer " + access))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.currency").value("NGN"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.availableBalance").value(0.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String walletId = mapper.readTree(walletBody).get("id").asText();
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/admin/wallets/" + walletId + "/status")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\"}"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT count(*) FROM wallets w JOIN users u ON u.id=w.owner_id WHERE u.email=?")) {
            statement.setString(1, email);
            try (var result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
        try (var connection = dataSource.getConnection();
                var duplicate = connection.prepareStatement(
                        """
                INSERT INTO wallets(id,owner_id,currency,status,available_balance,ledger_balance,created_at,updated_at)
                SELECT ?, id, 'NGN', 'ACTIVE', 0.00, 0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM users WHERE email=?
                """)) {
            duplicate.setObject(1, UUID.randomUUID());
            duplicate.setString(2, email);
            assertThatThrownBy(duplicate::executeUpdate).isInstanceOf(SQLException.class);
        }

        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO user_roles(user_id,role_id) SELECT id,2 FROM users WHERE email=?")) {
            statement.setString(1, email);
            statement.executeUpdate();
        }
        String adminLoginBody = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String adminAccess = mapper.readTree(adminLoginBody).get("accessToken").asText();
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/admin/wallets/" + walletId + "/status")
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("FROZEN"));
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/admin/wallets/" + walletId + "/status")
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/admin/wallets/" + walletId + "/status")
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("INVALID_WALLET_STATE_TRANSITION"));
    }

    @Test
    void walletFailureRollsBackCustomerCreation() throws Exception {
        String email = "rollback-" + UUID.randomUUID() + "@example.com";
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE OR REPLACE FUNCTION reject_test_wallet() RETURNS trigger AS $$
                    BEGIN
                      IF EXISTS (SELECT 1 FROM users WHERE id=NEW.owner_id AND email LIKE 'rollback-%') THEN
                        RAISE EXCEPTION 'test wallet rejection';
                      END IF;
                      RETURN NEW;
                    END; $$ LANGUAGE plpgsql
                    """);
            statement.execute("DROP TRIGGER IF EXISTS reject_test_wallet_trigger ON wallets");
            statement.execute(
                    "CREATE TRIGGER reject_test_wallet_trigger BEFORE INSERT ON wallets FOR EACH ROW EXECUTE FUNCTION reject_test_wallet() ");
        }
        assertThatThrownBy(() -> registration.register(email, "valid-placeholder-password-hash"))
                .isInstanceOf(RuntimeException.class);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT count(*) FROM users WHERE email=?")) {
            statement.setString(1, email);
            try (var result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isZero();
            }
        }
    }

    @Test
    void databaseRejectsInvalidWalletBalances() throws Exception {
        UUID owner = UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            try (var user = connection.prepareStatement(
                    """
                    INSERT INTO users(id,email,password_hash,status,created_at,updated_at)
                    VALUES (?, ?, 'hash', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                String email = owner + "@example.com";
                user.setObject(1, owner);
                user.setString(2, email);
                user.executeUpdate();
            }
            try (var wallet = connection.prepareStatement(
                    """
                    INSERT INTO wallets(id,owner_id,currency,status,available_balance,ledger_balance,created_at,updated_at)
                    VALUES (?, ?, 'NGN', 'ACTIVE', 10.00, 5.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                wallet.setObject(1, UUID.randomUUID());
                wallet.setObject(2, owner);
                assertThatThrownBy(wallet::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void balancedJournalPostsIsImmutableAndReconciliationDetectsProjectionDrift() throws Exception {
        String leftEmail = "ledger-left-" + UUID.randomUUID() + "@example.com";
        String rightEmail = "ledger-right-" + UUID.randomUUID() + "@example.com";
        var left = registration.register(leftEmail, "hash");
        var right = registration.register(rightEmail, "hash");
        var leftWallet = walletRepository
                .findByOwnerIdAndCurrency(left.id(), Currency.NGN)
                .orElseThrow();
        var rightWallet = walletRepository
                .findByOwnerIdAndCurrency(right.id(), Currency.NGN)
                .orElseThrow();
        var leftAccount = ledgerService.getAccountForWallet(leftWallet.getId());
        var rightAccount = ledgerService.getAccountForWallet(rightWallet.getId());
        BigDecimal debitAmount = new BigDecimal("25.00");
        BigDecimal mismatchedCreditAmount = new BigDecimal("24.00");
        LedgerEntryCommand debitEntry = new LedgerEntryCommand(leftAccount.getId(), EntryType.DEBIT, debitAmount);
        LedgerEntryCommand mismatchedCreditEntry =
                new LedgerEntryCommand(rightAccount.getId(), EntryType.CREDIT, mismatchedCreditAmount);
        List<LedgerEntryCommand> unbalancedEntries = List.of(debitEntry, mismatchedCreditEntry);
        String unbalancedReference = "UNBALANCED-" + UUID.randomUUID();
        LedgerPostingCommand unbalancedCommand = new LedgerPostingCommand(
                JournalSourceType.ADJUSTMENT, unbalancedReference, Currency.NGN, "invalid", unbalancedEntries);
        assertThatThrownBy(() -> ledgerService.post(unbalancedCommand)).isInstanceOf(UnbalancedJournalException.class);

        LedgerEntryCommand creditEntry = new LedgerEntryCommand(rightAccount.getId(), EntryType.CREDIT, debitAmount);
        List<LedgerEntryCommand> balancedEntries = List.of(debitEntry, creditEntry);
        String balancedReference = "TEST-" + UUID.randomUUID();
        LedgerPostingCommand balancedCommand = new LedgerPostingCommand(
                JournalSourceType.ADJUSTMENT, balancedReference, Currency.NGN, "balanced test", balancedEntries);
        var result = ledgerService.post(balancedCommand);
        assertThat(reconciliationService.reconcile(rightWallet.getId())).isPresent();
        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        "UPDATE journal_transactions SET description='tampered' WHERE id=?")) {
            update.setObject(1, result.journalId());
            assertThatThrownBy(update::executeUpdate).isInstanceOf(SQLException.class);
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        """
                SELECT sum(amount) FILTER(WHERE entry_type='DEBIT'),sum(amount) FILTER(WHERE entry_type='CREDIT')
                FROM journal_entries WHERE journal_transaction_id=?
                """)) {
            statement.setObject(1, result.journalId());
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo(rows.getBigDecimal(2));
            }
        }
    }

    @Test
    void postgresRejectsAnUnbalancedJournalAtCommit() throws Exception {
        String email = "unbalanced-" + UUID.randomUUID() + "@example.com";
        var user = registration.register(email, "hash");
        var wallet = walletRepository
                .findByOwnerIdAndCurrency(user.id(), Currency.NGN)
                .orElseThrow();
        var account = ledgerService.getAccountForWallet(wallet.getId());
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            UUID journal = UUID.randomUUID();
            try (var j = connection.prepareStatement(
                    "INSERT INTO journal_transactions(id,reference,source_type,source_reference,currency,posted_at,created_at) VALUES(?,?,'ADJUSTMENT',?,'NGN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                j.setObject(1, journal);
                j.setString(2, "JRN-" + journal);
                j.setString(3, "BAD-" + journal);
                j.executeUpdate();
            }
            try (var e = connection.prepareStatement(
                    "INSERT INTO journal_entries(id,journal_transaction_id,ledger_account_id,entry_sequence,entry_type,amount,currency,created_at) VALUES(?,?,?,1,'DEBIT',10.00,'NGN',CURRENT_TIMESTAMP)")) {
                e.setObject(1, UUID.randomUUID());
                e.setObject(2, journal);
                e.setObject(3, account.getId());
                e.executeUpdate();
            }
            assertThatThrownBy(connection::commit).isInstanceOf(SQLException.class);
            connection.rollback();
        }
    }

    @Test
    void internalTransferMovesProjectionsAndPostsBalancedLedgerAtomically() throws Exception {
        String senderEmail = "sender-" + UUID.randomUUID() + "@example.com",
                receiverEmail = "receiver-" + UUID.randomUUID() + "@example.com",
                password = "correct horse battery staple";
        var sender = registration.register(senderEmail, "$2a$12$wD8fH1OZGjysfjnUtUN0jeA3gS5zlpwiKNSI2nX2fFpNNh.KuHNDq");
        var receiver = registration.register(receiverEmail, "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), Currency.NGN)
                .orElseThrow();
        BigDecimal openingBalance = new BigDecimal("100.00");
        fundWallet(senderWallet.getId(), openingBalance);
        String access = issueAccessToken(sender.id(), senderEmail, password);
        String idempotencyKey = "test-transfer-" + UUID.randomUUID();
        String transferPayload = "{\"receiverWalletId\":\"" + receiverWallet.getId()
                + "\",\"amount\":25.00,\"currency\":\"NGN\",\"description\":\"test transfer\"}";
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferPayload))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("SUCCESSFUL"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String reference = mapper.readTree(body).get("reference").asText();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferPayload))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.reference").value(reference));
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverWalletId\":\"" + receiverWallet.getId()
                                + "\",\"amount\":20.00,\"currency\":\"NGN\"}"))
                .andExpect(MockMvcResultMatchers.status().isConflict());
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT (SELECT available_balance FROM wallets WHERE id=?),(SELECT available_balance FROM wallets WHERE id=?),(SELECT count(*) FROM journal_transactions WHERE source_type='TRANSFER' AND source_reference=?),(SELECT count(*) FROM transfers t JOIN idempotency_records i ON i.id=t.idempotency_record_id WHERE t.reference=? AND t.idempotency_key=? AND i.status='COMPLETED'),(SELECT count(*) FROM outbox_events o JOIN transfers t ON t.id=o.aggregate_id WHERE t.reference=? AND o.event_type='TransferCompleted')")) {
            statement.setObject(1, senderWallet.getId());
            statement.setObject(2, receiverWallet.getId());
            statement.setString(3, reference);
            statement.setString(4, reference);
            statement.setString(5, idempotencyKey);
            statement.setString(6, reference);
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("75.00");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("25.00");
                assertThat(rows.getInt(3)).isEqualTo(1);
                assertThat(rows.getInt(4)).isEqualTo(1);
                assertThat(rows.getInt(5)).isEqualTo(1);
            }
        }
        UUID transferId, eventId;
        Instant completedAt;
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT t.id,o.id,t.completed_at FROM transfers t JOIN outbox_events o ON o.aggregate_id=t.id WHERE t.reference=?")) {
            q.setString(1, reference);
            try (var rows = q.executeQuery()) {
                rows.next();
                transferId = (UUID) rows.getObject(1);
                eventId = (UUID) rows.getObject(2);
                completedAt = rows.getTimestamp(3).toInstant();
            }
        }
        var eventMessage = new TransferCompletedMessage(
                transferId, reference, senderWallet.getId(), receiverWallet.getId(), "25.00", "NGN", completedAt);
        assertThat(transferEventProcessingService.process(
                        eventId, "TransferCompleted", 1, transferId, "phase9-test", eventMessage))
                .isTrue();
        assertThat(transferEventProcessingService.process(
                        eventId, "TransferCompleted", 1, transferId, "phase9-test", eventMessage))
                .isFalse();
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT (SELECT count(*) FROM consumed_events WHERE event_id=? AND status='PROCESSED'),(SELECT count(*) FROM notifications WHERE event_id=?),(SELECT count(*) FROM audit_records WHERE event_id=?)")) {
            q.setObject(1, eventId);
            q.setObject(2, eventId);
            q.setObject(3, eventId);
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isOne();
                assertThat(rows.getInt(2)).isEqualTo(2);
                assertThat(rows.getInt(3)).isOne();
            }
        }
        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        "UPDATE audit_records SET correlation_id='tampered' WHERE event_id=?")) {
            update.setObject(1, eventId);
            assertThatThrownBy(update::executeUpdate).isInstanceOf(SQLException.class);
        }
        try (var connection = dataSource.getConnection();
                var delete = connection.prepareStatement("DELETE FROM audit_records WHERE event_id=?")) {
            delete.setObject(1, eventId);
            assertThatThrownBy(delete::executeUpdate).isInstanceOf(SQLException.class);
        }
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/transfers/" + reference)
                        .header("Authorization", "Bearer " + access))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void ledgerFailureRollsBackTransferAndBothWallets() throws Exception {
        String senderEmail = "rollback-transfer-" + UUID.randomUUID() + "@example.com";
        var sender = registration.register(senderEmail, "hash");
        var receiver = registration.register("rollback-receiver-" + UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), Currency.NGN)
                .orElseThrow();
        BigDecimal openingBalance = new BigDecimal("100.00");
        fundWallet(senderWallet.getId(), openingBalance);
        int outboxBefore;
        try (var connection = dataSource.getConnection();
                var q = connection.createStatement();
                var rows = q.executeQuery("SELECT count(*) FROM outbox_events")) {
            rows.next();
            outboxBefore = rows.getInt(1);
        }
        try (var connection = dataSource.getConnection();
                var sql = connection.createStatement()) {
            sql.execute(
                    "CREATE OR REPLACE FUNCTION reject_transfer_journal() RETURNS trigger AS $$ BEGIN IF NEW.source_type='TRANSFER' AND NEW.description='force rollback' THEN RAISE EXCEPTION 'forced ledger failure'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
            sql.execute("DROP TRIGGER IF EXISTS reject_transfer_journal_trigger ON journal_transactions");
            sql.execute(
                    "CREATE TRIGGER reject_transfer_journal_trigger BEFORE INSERT ON journal_transactions FOR EACH ROW EXECUTE FUNCTION reject_transfer_journal()");
        }
        BigDecimal transferAmount = new BigDecimal("30.00");
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(receiverWallet.getId(), transferAmount, Currency.NGN, "force rollback");
        assertThatThrownBy(() -> transferService.create(sender.id(), transferRequest))
                .isInstanceOf(RuntimeException.class);
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT (SELECT available_balance FROM wallets WHERE id=?),(SELECT available_balance FROM wallets WHERE id=?),(SELECT count(*) FROM transfers WHERE sender_wallet_id=? AND description='force rollback'),(SELECT count(*) FROM outbox_events)")) {
            q.setObject(1, senderWallet.getId());
            q.setObject(2, receiverWallet.getId());
            q.setObject(3, senderWallet.getId());
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("100.00");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("0.00");
                assertThat(rows.getInt(3)).isZero();
                assertThat(rows.getInt(4)).isEqualTo(outboxBefore);
            }
        }
    }

    @Test
    void concurrentIdenticalIdempotencyKeysCreateExactlyOneTransfer() throws Exception {
        var sender = registration.register("idempotency-sender-" + UUID.randomUUID() + "@example.com", "hash");
        var receiver = registration.register("idempotency-receiver-" + UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), Currency.NGN)
                .orElseThrow();
        BigDecimal openingBalance = new BigDecimal("100.00");
        fundWallet(senderWallet.getId(), openingBalance);
        BigDecimal transferAmount = new BigDecimal("15.00");
        var request = new CreateTransferRequest(receiverWallet.getId(), transferAmount, Currency.NGN, "duplicate race");
        String key = "concurrent-key-" + UUID.randomUUID();
        int attempts = 12;
        var start = new CountDownLatch(1);
        var completed = new AtomicInteger();
        var references = new ConcurrentLinkedQueue<String>();
        var failures = new ConcurrentLinkedQueue<Throwable>();
        var executor = Executors.newFixedThreadPool(attempts);
        try {
            for (int i = 0; i < attempts; i++)
                executor.submit(() -> {
                    try {
                        start.await();
                        references.add(idempotencyService
                                .executeTransfer(sender.id(), key, request)
                                .reference());
                    } catch (Throwable failure) {
                        failures.add(failure);
                    } finally {
                        completed.incrementAndGet();
                    }
                });
            start.countDown();
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAtomic(completed, Matchers.equalTo(attempts));
        } finally {
            executor.shutdownNow();
        }
        assertThat(failures).isEmpty();
        assertThat(references).hasSize(attempts);
        assertThat(references.stream().distinct()).hasSize(1);
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT (SELECT available_balance FROM wallets WHERE id=?),(SELECT available_balance FROM wallets WHERE id=?),(SELECT count(*) FROM transfers WHERE idempotency_key=?),(SELECT count(*) FROM idempotency_records WHERE client_identity=? AND idempotency_key=? AND status='COMPLETED'),(SELECT count(*) FROM journal_transactions WHERE source_type='TRANSFER' AND description='duplicate race')")) {
            q.setObject(1, senderWallet.getId());
            q.setObject(2, receiverWallet.getId());
            q.setString(3, key);
            q.setObject(4, sender.id());
            q.setString(5, key);
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("85.00");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("15.00");
                assertThat(rows.getInt(3)).isEqualTo(1);
                assertThat(rows.getInt(4)).isEqualTo(1);
                assertThat(rows.getInt(5)).isEqualTo(1);
            }
        }
    }

    @Test
    void concurrentDebitsOnlyAllowAffordableTransfersAndPreserveMoney() throws Exception {
        var sender = registration.register("concurrency-sender-" + UUID.randomUUID() + "@example.com", "hash");
        var receiver = registration.register("concurrency-receiver-" + UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), Currency.NGN)
                .orElseThrow();
        BigDecimal openingBalance = new BigDecimal("100.00");
        fundWallet(senderWallet.getId(), openingBalance);
        int attempts = 20;
        var start = new CountDownLatch(1);
        var completed = new AtomicInteger();
        var successes = new AtomicInteger();
        var rejected = new AtomicInteger();
        var unexpected = new ConcurrentLinkedQueue<Throwable>();
        var executor = Executors.newFixedThreadPool(12);
        try {
            for (int i = 0; i < attempts; i++)
                executor.submit(() -> {
                    try {
                        start.await();
                        BigDecimal transferAmount = new BigDecimal("10.00");
                        CreateTransferRequest transferRequest = new CreateTransferRequest(
                                receiverWallet.getId(), transferAmount, Currency.NGN, "concurrent debit");
                        transferService.create(sender.id(), transferRequest);
                        successes.incrementAndGet();
                    } catch (DomainException expected) {
                        rejected.incrementAndGet();
                    } catch (Throwable failure) {
                        unexpected.add(failure);
                    } finally {
                        completed.incrementAndGet();
                    }
                });
            start.countDown();
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAtomic(completed, Matchers.equalTo(attempts));
        } finally {
            executor.shutdownNow();
        }
        assertThat(unexpected).isEmpty();
        assertThat(successes.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(10);
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT (SELECT available_balance FROM wallets WHERE id=?),(SELECT available_balance FROM wallets WHERE id=?),(SELECT count(*) FROM transfers WHERE sender_wallet_id=? AND status='SUCCESSFUL'),(SELECT count(*) FROM journal_transactions WHERE source_type='TRANSFER' AND description='concurrent debit')")) {
            q.setObject(1, senderWallet.getId());
            q.setObject(2, receiverWallet.getId());
            q.setObject(3, senderWallet.getId());
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("0.00");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("100.00");
                assertThat(rows.getBigDecimal(1).add(rows.getBigDecimal(2))).isEqualByComparingTo("100.00");
                assertThat(rows.getInt(3)).isEqualTo(10);
                assertThat(rows.getInt(4)).isEqualTo(10);
            }
        }
        try (var connection = dataSource.getConnection();
                var q = connection.createStatement();
                var rows = q.executeQuery("SELECT count(*) FROM wallets WHERE available_balance<0")) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
        }
    }

    @Test
    void successfulReversalIsIdempotentBalancedAuditedAndImmutable() throws Exception {
        var sender = registration.register("reversal-sender-" + UUID.randomUUID() + "@example.com", "hash");
        var receiver = registration.register("reversal-receiver-" + UUID.randomUUID() + "@example.com", "hash");
        var admin = registration.register("reversal-admin-" + UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), Currency.NGN)
                .orElseThrow();
        BigDecimal openingBalance = new BigDecimal("100.00");
        fundWallet(senderWallet.getId(), openingBalance);
        BigDecimal transferAmount = new BigDecimal("25.00");
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(receiverWallet.getId(), transferAmount, Currency.NGN, "reversible transfer");
        var original = transferService.create(sender.id(), transferRequest);
        String key = "reversal-key-" + UUID.randomUUID();
        var request = new CreateReversalRequest("confirmed duplicate");
        var reversal = transferReversalService.reverse(admin.id(), original.reference(), key, request);
        assertThat(transferReversalService
                        .reverse(admin.id(), original.reference(), key, request)
                        .reversalReference())
                .isEqualTo(reversal.reversalReference());
        assertThatThrownBy(() -> transferReversalService.reverse(
                        admin.id(), original.reference(), "different-key-" + UUID.randomUUID(), request))
                .isInstanceOf(ReversalAlreadyExistsException.class);
        CreateReversalRequest reversalRequest = new CreateReversalRequest("different reason");
        assertThatThrownBy(
                        () -> transferReversalService.reverse(admin.id(), original.reference(), key, reversalRequest))
                .isInstanceOf(ReversalIdempotencyConflictException.class);
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT (SELECT available_balance FROM wallets WHERE id=?),(SELECT available_balance FROM wallets WHERE id=?),(SELECT status FROM transfers WHERE reference=?),(SELECT count(*) FROM transfer_reversals WHERE original_transfer_reference=? AND status='SUCCESSFUL'),(SELECT count(*) FROM journal_transactions WHERE source_type='REVERSAL' AND source_reference=?),(SELECT count(*) FROM audit_records WHERE action='TRANSFER_REVERSED' AND details->>'originalTransferReference'=?),(SELECT count(*) FROM outbox_events WHERE event_type='TransferReversed' AND aggregate_id=(SELECT id FROM transfers WHERE reference=?))")) {
            q.setObject(1, senderWallet.getId());
            q.setObject(2, receiverWallet.getId());
            q.setString(3, original.reference());
            q.setString(4, original.reference());
            q.setString(5, reversal.reversalReference());
            q.setString(6, original.reference());
            q.setString(7, original.reference());
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("100.00");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("0.00");
                assertThat(rows.getString(3)).isEqualTo("REVERSED");
                assertThat(rows.getInt(4)).isOne();
                assertThat(rows.getInt(5)).isOne();
                assertThat(rows.getInt(6)).isOne();
                assertThat(rows.getInt(7)).isOne();
            }
        }
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT j.source_type,count(e.id),sum(e.amount) FILTER(WHERE e.entry_type='DEBIT'),sum(e.amount) FILTER(WHERE e.entry_type='CREDIT') FROM journal_transactions j JOIN journal_entries e ON e.journal_transaction_id=j.id WHERE j.source_reference IN (?,?) GROUP BY j.source_type ORDER BY j.source_type")) {
            q.setString(1, original.reference());
            q.setString(2, reversal.reversalReference());
            try (var rows = q.executeQuery()) {
                int journals = 0;
                while (rows.next()) {
                    journals++;
                    assertThat(rows.getInt(2)).isEqualTo(2);
                    assertThat(rows.getBigDecimal(3)).isEqualByComparingTo(rows.getBigDecimal(4));
                }
                assertThat(journals).isEqualTo(2);
            }
        }
    }

    @Test
    void reversalWithInsufficientReceiverFundsRollsBackCompletely() throws Exception {
        var sender = registration.register("poor-reversal-sender-" + UUID.randomUUID() + "@example.com", "hash");
        var receiver = registration.register("poor-reversal-receiver-" + UUID.randomUUID() + "@example.com", "hash");
        var third = registration.register("poor-reversal-third-" + UUID.randomUUID() + "@example.com", "hash");
        var admin = registration.register("poor-reversal-admin-" + UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), Currency.NGN)
                .orElseThrow();
        var thirdWallet = walletRepository
                .findByOwnerIdAndCurrency(third.id(), Currency.NGN)
                .orElseThrow();
        BigDecimal openingBalance = new BigDecimal("50.00");
        fundWallet(senderWallet.getId(), openingBalance);
        BigDecimal transferAmount = new BigDecimal("25.00");
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(receiverWallet.getId(), transferAmount, Currency.NGN, "cannot reverse");
        var original = transferService.create(sender.id(), transferRequest);
        BigDecimal transferAmount2 = new BigDecimal("20.00");
        CreateTransferRequest transferRequest2 =
                new CreateTransferRequest(thirdWallet.getId(), transferAmount2, Currency.NGN, "receiver spent funds");
        transferService.create(receiver.id(), transferRequest2);
        CreateReversalRequest reversalRequest = new CreateReversalRequest("attempt reversal");
        assertThatThrownBy(() -> transferReversalService.reverse(
                        admin.id(),
                        original.reference(),
                        "insufficient-reversal-" + UUID.randomUUID(),
                        reversalRequest))
                .isInstanceOf(WalletTransferRejectedException.class);
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT (SELECT status FROM transfers WHERE reference=?),(SELECT count(*) FROM transfer_reversals WHERE original_transfer_reference=?),(SELECT count(*) FROM journal_transactions WHERE source_type='REVERSAL' AND source_reference LIKE 'REV-%'),(SELECT available_balance FROM wallets WHERE id=?)")) {
            q.setString(1, original.reference());
            q.setString(2, original.reference());
            q.setObject(3, receiverWallet.getId());
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("SUCCESSFUL");
                assertThat(rows.getInt(2)).isZero();
                assertThat(rows.getBigDecimal(4)).isEqualByComparingTo("5.00");
            }
        }
    }

    @Test
    void customerCannotCallAdministrativeReversalEndpoint() throws Exception {
        String email = "reversal-customer-" + UUID.randomUUID() + "@example.com",
                password = "correct horse battery staple";
        var customer = registration.register(email, "hash");
        String access = issueAccessToken(customer.id(), email, password);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/transfers/TRF-NOT-RELEVANT/reverse")
                        .header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", "forbidden-reversal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"not authorized\"}"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void concurrentReversalRequestsProduceOneCompensatingJournal() throws Exception {
        var sender = registration.register("concurrent-reversal-sender-" + UUID.randomUUID() + "@example.com", "hash");
        var receiver =
                registration.register("concurrent-reversal-receiver-" + UUID.randomUUID() + "@example.com", "hash");
        var admin = registration.register("concurrent-reversal-admin-" + UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), Currency.NGN)
                .orElseThrow();
        BigDecimal openingBalance = new BigDecimal("40.00");
        fundWallet(senderWallet.getId(), openingBalance);
        BigDecimal transferAmount = new BigDecimal("15.00");
        CreateTransferRequest transferRequest = new CreateTransferRequest(
                receiverWallet.getId(), transferAmount, Currency.NGN, "concurrent reversal original");
        var original = transferService.create(sender.id(), transferRequest);
        int attempts = 10;
        var start = new CountDownLatch(1);
        var done = new AtomicInteger();
        var successful = new AtomicInteger();
        var rejected = new AtomicInteger();
        var unexpected = new ConcurrentLinkedQueue<Throwable>();
        var executor = Executors.newFixedThreadPool(attempts);
        try {
            for (int i = 0; i < attempts; i++) {
                String key = "concurrent-reversal-" + i + "-" + UUID.randomUUID();
                executor.submit(() -> {
                    try {
                        start.await();
                        CreateReversalRequest reversalRequest =
                                new CreateReversalRequest("concurrent operations reversal");
                        transferReversalService.reverse(admin.id(), original.reference(), key, reversalRequest);
                        successful.incrementAndGet();
                    } catch (DomainException expected) {
                        rejected.incrementAndGet();
                    } catch (Throwable failure) {
                        unexpected.add(failure);
                    } finally {
                        done.incrementAndGet();
                    }
                });
            }
            start.countDown();
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAtomic(done, Matchers.equalTo(attempts));
        } finally {
            executor.shutdownNow();
        }
        assertThat(unexpected).isEmpty();
        assertThat(successful).hasValue(1);
        assertThat(rejected).hasValue(attempts - 1);
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT (SELECT count(*) FROM transfer_reversals WHERE original_transfer_reference=?),(SELECT count(*) FROM journal_transactions WHERE source_type='REVERSAL' AND description='concurrent operations reversal'),(SELECT available_balance FROM wallets WHERE id=?),(SELECT available_balance FROM wallets WHERE id=?)")) {
            q.setString(1, original.reference());
            q.setObject(2, senderWallet.getId());
            q.setObject(3, receiverWallet.getId());
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isOne();
                assertThat(rows.getInt(2)).isOne();
                assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("40.00");
                assertThat(rows.getBigDecimal(4)).isEqualByComparingTo("0.00");
            }
        }
    }

    @Test
    void financialReconciliationDetectsStableCaseAndRepairsOnlyTheWalletProjection() throws Exception {
        var owner = registration.register("financial-reconciliation-" + UUID.randomUUID() + "@example.com", "hash");
        var operator = registration.register(
                "financial-reconciliation-operator-" + UUID.randomUUID() + "@example.com", "hash");
        var wallet = walletRepository
                .findByOwnerIdAndCurrency(owner.id(), Currency.NGN)
                .orElseThrow();
        BigDecimal openingBalance = new BigDecimal("50.00");
        fundWallet(wallet.getId(), openingBalance);

        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        "UPDATE wallets SET ledger_balance = 77.00, available_balance = 76.00 WHERE id = ?")) {
            update.setObject(1, wallet.getId());
            update.executeUpdate();
        }

        var firstRun = financialReconciliationService.run(operator.id());
        assertThat(firstRun.status()).isEqualTo("COMPLETED");
        assertThat(firstRun.discrepancyCount()).isPositive();
        financialReconciliationService.run(operator.id());

        UUID caseId;
        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement(
                        "SELECT id, status, severity, count(*) OVER() FROM reconciliation_cases "
                                + "WHERE case_key = ?")) {
            query.setString(1, "WALLET_PROJECTION:" + wallet.getId());
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                caseId = (UUID) rows.getObject(1);
                assertThat(rows.getString(2)).isEqualTo("OPEN");
                assertThat(rows.getString(3)).isEqualTo("HIGH");
                assertThat(rows.getInt(4)).isOne();
            }
        }

        String key = "projection-repair-" + UUID.randomUUID();
        var request = new RepairWalletProjectionRequest(
                "Restore the materialized wallet balance from immutable ledger entries");
        var repair = reconciliationRepairService.repairWallet(operator.id(), caseId, key, request);
        assertThat(reconciliationRepairService
                        .repairWallet(operator.id(), caseId, key, request)
                        .repairId())
                .isEqualTo(repair.repairId());
        RepairWalletProjectionRequest repairRequest = new RepairWalletProjectionRequest("Different repair request");
        assertThatThrownBy(() -> reconciliationRepairService.repairWallet(operator.id(), caseId, key, repairRequest))
                .isInstanceOf(ReconciliationRepairConflictException.class);

        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement("SELECT w.ledger_balance, w.available_balance, c.status, "
                        + "(SELECT count(*) FROM reconciliation_repairs WHERE case_id = c.id), "
                        + "(SELECT count(*) FROM audit_records WHERE action = 'RECONCILIATION_REPAIR' "
                        + "AND event_id = ?) "
                        + "FROM wallets w JOIN reconciliation_cases c ON c.resource_id = w.id "
                        + "WHERE w.id = ? AND c.id = ?")) {
            query.setObject(1, repair.repairId());
            query.setObject(2, wallet.getId());
            query.setObject(3, caseId);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("50.00");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("50.00");
                assertThat(rows.getString(3)).isEqualTo("RESOLVED");
                assertThat(rows.getInt(4)).isOne();
                assertThat(rows.getInt(5)).isOne();
            }
        }

        financialReconciliationService.run(operator.id());
        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement("SELECT status FROM reconciliation_cases WHERE id = ?")) {
            query.setObject(1, caseId);
            try (var rows = query.executeQuery()) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("RESOLVED");
            }
        }
    }

    @Test
    void financialReconciliationDetectsSuccessfulTransferWithoutItsRequiredJournal() throws Exception {
        var sender = registration.register("bad-transfer-sender-" + UUID.randomUUID() + "@example.com", "hash");
        var receiver = registration.register("bad-transfer-receiver-" + UUID.randomUUID() + "@example.com", "hash");
        var operator = registration.register("bad-transfer-operator-" + UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), Currency.NGN)
                .orElseThrow();
        UUID transferId = UUID.randomUUID();
        String reference = "TRF-BROKEN-" + UUID.randomUUID();
        try (var connection = dataSource.getConnection();
                var insert = connection.prepareStatement(
                        "INSERT INTO transfers(id,reference,sender_wallet_id,receiver_wallet_id,amount,currency,"
                                + "description,status,created_at,updated_at,completed_at) "
                                + "VALUES(?,?,?,?,10.00,'NGN','missing journal','SUCCESSFUL',"
                                + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            insert.setObject(1, transferId);
            insert.setString(2, reference);
            insert.setObject(3, senderWallet.getId());
            insert.setObject(4, receiverWallet.getId());
            insert.executeUpdate();
        }

        financialReconciliationService.run(operator.id());
        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement(
                        "SELECT severity, status FROM reconciliation_cases WHERE case_key = ?")) {
            query.setString(1, "INTERNAL_TRANSFER_LEDGER:" + transferId);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("CRITICAL");
                assertThat(rows.getString(2)).isEqualTo("OPEN");
            }
        }
    }

    @Test
    void customerCannotCallFinancialReconciliationEndpoints() throws Exception {
        String email = "reconciliation-customer-" + UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";
        var customer = registration.register(email, "hash");
        String access = issueAccessToken(customer.id(), email, password);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/reconciliation/runs")
                        .header("Authorization", "Bearer " + access))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void operationalMetricsExposeCommittedTransfersAndRealOutboxBacklog() throws Exception {
        var sender = registration.register("metrics-sender-" + UUID.randomUUID() + "@example.com", "hash");
        var receiver = registration.register("metrics-receiver-" + UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), Currency.NGN)
                .orElseThrow();
        BigDecimal openingBalance = new BigDecimal("20.00");
        fundWallet(senderWallet.getId(), openingBalance);
        double before = meterRegistry
                .counter("wallet.transfers", "type", "internal", "outcome", "successful")
                .count();

        BigDecimal transferAmount = new BigDecimal("5.00");
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(receiverWallet.getId(), transferAmount, Currency.NGN, "observable transfer");
        transferService.create(sender.id(), transferRequest);
        operationalMetricsService.refresh();

        assertThat(meterRegistry
                        .counter("wallet.transfers", "type", "internal", "outcome", "successful")
                        .count())
                .isEqualTo(before + 1);
        assertThat(meterRegistry
                        .find("wallet.transfer.duration")
                        .tags("type", "internal", "outcome", "successful")
                        .timer()
                        .count())
                .isPositive();
        long expectedPending;
        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement(
                        "SELECT count(*) FROM outbox_events WHERE status IN ('PENDING','FAILED')");
                var rows = query.executeQuery()) {
            rows.next();
            expectedPending = rows.getLong(1);
        }
        assertThat(meterRegistry
                        .find("wallet.outbox.backlog")
                        .tag("status", "pending")
                        .gauge()
                        .value())
                .isEqualTo(expectedPending);
    }

    private String issueAccessToken(UUID userId, String email, String password) throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
            String passwordHash = encoder.encode(password);
            statement.setString(1, passwordHash);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
        String login = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(login).get("accessToken").asText();
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
