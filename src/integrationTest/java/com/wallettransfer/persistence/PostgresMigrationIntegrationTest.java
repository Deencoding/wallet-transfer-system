package com.wallettransfer.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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

    @Autowired
    DataSource dataSource;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    com.wallettransfer.authentication.service.CustomerRegistrationService registration;

    @Autowired
    com.wallettransfer.ledger.service.LedgerService ledgerService;

    @Autowired
    com.wallettransfer.ledger.service.LedgerReconciliationService reconciliationService;

    @Autowired
    com.wallettransfer.wallets.repository.WalletRepository walletRepository;

    @Autowired
    com.wallettransfer.transfers.service.TransferService transferService;

    @Autowired
    com.wallettransfer.idempotency.service.IdempotencyService idempotencyService;

    @Autowired
    com.wallettransfer.messaging.service.TransferEventProcessingService transferEventProcessingService;

    @Autowired
    com.wallettransfer.externaltransfers.service.ExternalTransferService externalTransferService;

    @Autowired
    com.wallettransfer.externaltransfers.service.ExternalTransferCompletionService externalCompletionService;

    @Autowired
    com.wallettransfer.providersimulator.service.ProviderSimulatorService providerSimulatorService;

    @Autowired
    com.wallettransfer.providers.service.ProviderWebhookService providerWebhookService;

    @Autowired
    com.wallettransfer.providers.service.ResilientProviderTransferService resilientProviderTransferService;

    @Autowired
    com.wallettransfer.reconciliation.service.ExternalTransferReconciliationWorker reconciliationWorker;

    @Autowired
    com.wallettransfer.reconciliation.service.FinancialReconciliationService financialReconciliationService;

    @Autowired
    com.wallettransfer.reconciliation.service.ReconciliationRepairService reconciliationRepairService;

    @Autowired
    com.wallettransfer.reversals.service.TransferReversalService transferReversalService;

    @Autowired
    com.wallettransfer.shared.observability.service.OperationalMetricsService operationalMetricsService;

    @Autowired
    io.micrometer.core.instrument.MeterRegistry meterRegistry;

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
    void v3BackfillsAnNgnWalletForAnExistingCustomer() throws Exception {
        String schema =
                "phase3_backfill_" + java.util.UUID.randomUUID().toString().replace("-", "");
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target("2")
                .load()
                .migrate();
        java.util.UUID userId = java.util.UUID.randomUUID();
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            try (var user = connection.prepareStatement(
                    """
                    INSERT INTO users(id,email,email_normalized,password_hash,status,created_at,updated_at)
                    VALUES (?, 'existing@example.com', 'existing@example.com', 'hash', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                user.setObject(1, userId);
                user.executeUpdate();
            }
            try (var role = connection.prepareStatement("INSERT INTO user_roles(user_id,role_id) VALUES (?,1)")) {
                role.setObject(1, userId);
                role.executeUpdate();
            }
        }
        org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            try (var result =
                    statement.executeQuery("SELECT currency,status FROM wallets WHERE owner_id='" + userId + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("NGN");
                assertThat(result.getString(2)).isEqualTo("ACTIVE");
            }
        }
    }

    @Test
    void registrationLoginProfileRotationReuseAndLogoutWorkEndToEnd() throws Exception {
        String email = "phase2-" + java.util.UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.roles[0]")
                        .value("CUSTOMER"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong password value\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Email or password is incorrect"));

        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement("SELECT principal_hash, metadata::text FROM security_events "
                        + "WHERE event_type='AUTHENTICATION' AND outcome='FAILURE' ORDER BY occurred_at DESC LIMIT 1");
                var rows = query.executeQuery()) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).hasSize(64).doesNotContain(email);
            assertThat(rows.getString(2)).doesNotContain(email).doesNotContain("wrong password value");
        }

        String loginBody = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        com.fasterxml.jackson.databind.JsonNode login =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(loginBody);
        String access = login.get("accessToken").asText();
        String originalRefresh = login.get("refreshToken").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.email")
                        .value(email));

        String rotatedBody = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + originalRefresh + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String rotatedRefresh = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(rotatedBody)
                .get("refreshToken")
                .asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + originalRefresh + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("REFRESH_TOKEN_REVOKED"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isUnauthorized());

        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        "UPDATE security_events SET outcome='SUCCESS' WHERE event_type='AUTHENTICATION'")) {
            assertThatThrownBy(update::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }

        String secondLoginBody = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        com.fasterxml.jackson.databind.JsonNode secondLogin =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(secondLoginBody);
        String secondAccess = secondLogin.get("accessToken").asText();
        String secondRefresh = secondLogin.get("refreshToken").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + secondAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isUnauthorized());
    }

    @Test
    void concurrentRefreshRequestsCannotCreateTwoValidTokenChains() throws Exception {
        String email = "concurrent-" + java.util.UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String refresh = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body)
                .get("refreshToken")
                .asText();
        String payload = "{\"refreshToken\":\"" + refresh + "\"}";
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.List<java.util.concurrent.Future<Integer>> results = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                                "/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }))
                    .toList();
            start.countDown();
            java.util.List<Integer> statuses = results.stream()
                    .map(result -> {
                        try {
                            return result.get(10, java.util.concurrent.TimeUnit.SECONDS);
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
        String validPayload = "{\"email\":\"secure-" + java.util.UUID.randomUUID()
                + "@example.com\",\"password\":\"correct horse battery staple\"}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Frame-Options", "DENY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists("Content-Security-Policy"));

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"nobody@example.com\",\"password\":\"long enough password\",\"admin\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(70_000)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isPayloadTooLarge())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("REQUEST_TOO_LARGE"));
    }

    @Test
    void databaseEnforcesEmailUniqueness() throws Exception {
        try (var connection = dataSource.getConnection()) {
            java.util.UUID first = java.util.UUID.randomUUID();
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
                statement.setObject(1, java.util.UUID.randomUUID());
                assertThatThrownBy(statement::executeUpdate).isInstanceOf(java.sql.SQLException.class);
            }
        }
    }

    @Test
    void registrationCreatesExactlyOneWalletAndCustomerCanRetrieveIt() throws Exception {
        String email = "wallet-" + java.util.UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isCreated());
        String loginBody = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String access = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginBody)
                .get("accessToken")
                .asText();
        String walletBody = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/wallets/me")
                                .header("Authorization", "Bearer " + access))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.currency")
                        .value("NGN"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status")
                        .value("ACTIVE"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.availableBalance")
                                .value(0.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String walletId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(walletBody)
                .get("id")
                .asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/admin/wallets/" + walletId + "/status")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isForbidden());
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
            duplicate.setObject(1, java.util.UUID.randomUUID());
            duplicate.setString(2, email);
            assertThatThrownBy(duplicate::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }

        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO user_roles(user_id,role_id) SELECT id,2 FROM users WHERE email=?")) {
            statement.setString(1, email);
            statement.executeUpdate();
        }
        String adminLoginBody = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String adminAccess = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(adminLoginBody)
                .get("accessToken")
                .asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/admin/wallets/" + walletId + "/status")
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status")
                        .value("FROZEN"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/admin/wallets/" + walletId + "/status")
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/admin/wallets/" + walletId + "/status")
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_WALLET_STATE_TRANSITION"));
    }

    @Test
    void walletFailureRollsBackCustomerCreation() throws Exception {
        String email = "rollback-" + java.util.UUID.randomUUID() + "@example.com";
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
        java.util.UUID owner = java.util.UUID.randomUUID();
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
                wallet.setObject(1, java.util.UUID.randomUUID());
                wallet.setObject(2, owner);
                assertThatThrownBy(wallet::executeUpdate).isInstanceOf(java.sql.SQLException.class);
            }
        }
    }

    @Test
    void balancedJournalPostsIsImmutableAndReconciliationDetectsProjectionDrift() throws Exception {
        String leftEmail = "ledger-left-" + java.util.UUID.randomUUID() + "@example.com";
        String rightEmail = "ledger-right-" + java.util.UUID.randomUUID() + "@example.com";
        var left = registration.register(leftEmail, "hash");
        var right = registration.register(rightEmail, "hash");
        var leftWallet = walletRepository
                .findByOwnerIdAndCurrency(left.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var rightWallet = walletRepository
                .findByOwnerIdAndCurrency(right.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var leftAccount = ledgerService.getAccountForWallet(leftWallet.getId());
        var rightAccount = ledgerService.getAccountForWallet(rightWallet.getId());
        assertThatThrownBy(() -> ledgerService.post(new com.wallettransfer.ledger.dto.LedgerPostingCommand(
                        com.wallettransfer.ledger.model.JournalSourceType.ADJUSTMENT,
                        "UNBALANCED-" + java.util.UUID.randomUUID(),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "invalid",
                        java.util.List.of(
                                new com.wallettransfer.ledger.dto.LedgerEntryCommand(
                                        leftAccount.getId(),
                                        com.wallettransfer.ledger.model.EntryType.DEBIT,
                                        new java.math.BigDecimal("25.00")),
                                new com.wallettransfer.ledger.dto.LedgerEntryCommand(
                                        rightAccount.getId(),
                                        com.wallettransfer.ledger.model.EntryType.CREDIT,
                                        new java.math.BigDecimal("24.00"))))))
                .isInstanceOf(com.wallettransfer.ledger.exception.UnbalancedJournalException.class);
        var result = ledgerService.post(new com.wallettransfer.ledger.dto.LedgerPostingCommand(
                com.wallettransfer.ledger.model.JournalSourceType.ADJUSTMENT,
                "TEST-" + java.util.UUID.randomUUID(),
                com.wallettransfer.shared.money.Currency.NGN,
                "balanced test",
                java.util.List.of(
                        new com.wallettransfer.ledger.dto.LedgerEntryCommand(
                                leftAccount.getId(),
                                com.wallettransfer.ledger.model.EntryType.DEBIT,
                                new java.math.BigDecimal("25.00")),
                        new com.wallettransfer.ledger.dto.LedgerEntryCommand(
                                rightAccount.getId(),
                                com.wallettransfer.ledger.model.EntryType.CREDIT,
                                new java.math.BigDecimal("25.00")))));
        assertThat(reconciliationService.reconcile(rightWallet.getId())).isPresent();
        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        "UPDATE journal_transactions SET description='tampered' WHERE id=?")) {
            update.setObject(1, result.journalId());
            assertThatThrownBy(update::executeUpdate).isInstanceOf(java.sql.SQLException.class);
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
        String email = "unbalanced-" + java.util.UUID.randomUUID() + "@example.com";
        var user = registration.register(email, "hash");
        var wallet = walletRepository
                .findByOwnerIdAndCurrency(user.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var account = ledgerService.getAccountForWallet(wallet.getId());
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            java.util.UUID journal = java.util.UUID.randomUUID();
            try (var j = connection.prepareStatement(
                    "INSERT INTO journal_transactions(id,reference,source_type,source_reference,currency,posted_at,created_at) VALUES(?,?,'ADJUSTMENT',?,'NGN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                j.setObject(1, journal);
                j.setString(2, "JRN-" + journal);
                j.setString(3, "BAD-" + journal);
                j.executeUpdate();
            }
            try (var e = connection.prepareStatement(
                    "INSERT INTO journal_entries(id,journal_transaction_id,ledger_account_id,entry_sequence,entry_type,amount,currency,created_at) VALUES(?,?,?,1,'DEBIT',10.00,'NGN',CURRENT_TIMESTAMP)")) {
                e.setObject(1, java.util.UUID.randomUUID());
                e.setObject(2, journal);
                e.setObject(3, account.getId());
                e.executeUpdate();
            }
            assertThatThrownBy(connection::commit).isInstanceOf(java.sql.SQLException.class);
            connection.rollback();
        }
    }

    @Test
    void internalTransferMovesProjectionsAndPostsBalancedLedgerAtomically() throws Exception {
        String senderEmail = "sender-" + java.util.UUID.randomUUID() + "@example.com",
                receiverEmail = "receiver-" + java.util.UUID.randomUUID() + "@example.com",
                password = "correct horse battery staple";
        var sender = registration.register(senderEmail, "$2a$12$wD8fH1OZGjysfjnUtUN0jeA3gS5zlpwiKNSI2nX2fFpNNh.KuHNDq");
        var receiver = registration.register(receiverEmail, "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(senderWallet.getId(), new java.math.BigDecimal("100.00"));
        String access = issueAccessToken(sender.id(), senderEmail, password);
        String idempotencyKey = "test-transfer-" + java.util.UUID.randomUUID();
        String transferPayload = "{\"receiverWalletId\":\"" + receiverWallet.getId()
                + "\",\"amount\":25.00,\"currency\":\"NGN\",\"description\":\"test transfer\"}";
        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + access)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferPayload))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status")
                        .value("SUCCESSFUL"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String reference = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body)
                .get("reference")
                .asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferPayload))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.reference")
                        .value(reference));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverWalletId\":\"" + receiverWallet.getId()
                                + "\",\"amount\":20.00,\"currency\":\"NGN\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isConflict());
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
        java.util.UUID transferId, eventId;
        java.time.Instant completedAt;
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT t.id,o.id,t.completed_at FROM transfers t JOIN outbox_events o ON o.aggregate_id=t.id WHERE t.reference=?")) {
            q.setString(1, reference);
            try (var rows = q.executeQuery()) {
                rows.next();
                transferId = (java.util.UUID) rows.getObject(1);
                eventId = (java.util.UUID) rows.getObject(2);
                completedAt = rows.getTimestamp(3).toInstant();
            }
        }
        var eventMessage = new com.wallettransfer.messaging.dto.TransferCompletedMessage(
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
            assertThatThrownBy(update::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
        try (var connection = dataSource.getConnection();
                var delete = connection.prepareStatement("DELETE FROM audit_records WHERE event_id=?")) {
            delete.setObject(1, eventId);
            assertThatThrownBy(delete::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/transfers/" + reference)
                        .header("Authorization", "Bearer " + access))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isOk());
    }

    @Test
    void ledgerFailureRollsBackTransferAndBothWallets() throws Exception {
        String senderEmail = "rollback-transfer-" + java.util.UUID.randomUUID() + "@example.com";
        var sender = registration.register(senderEmail, "hash");
        var receiver =
                registration.register("rollback-receiver-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(senderWallet.getId(), new java.math.BigDecimal("100.00"));
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
        assertThatThrownBy(() -> transferService.create(
                        sender.id(),
                        new com.wallettransfer.transfers.dto.CreateTransferRequest(
                                receiverWallet.getId(),
                                new java.math.BigDecimal("30.00"),
                                com.wallettransfer.shared.money.Currency.NGN,
                                "force rollback")))
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
        var sender =
                registration.register("idempotency-sender-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var receiver =
                registration.register("idempotency-receiver-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(senderWallet.getId(), new java.math.BigDecimal("100.00"));
        var request = new com.wallettransfer.transfers.dto.CreateTransferRequest(
                receiverWallet.getId(),
                new java.math.BigDecimal("15.00"),
                com.wallettransfer.shared.money.Currency.NGN,
                "duplicate race");
        String key = "concurrent-key-" + java.util.UUID.randomUUID();
        int attempts = 12;
        var start = new java.util.concurrent.CountDownLatch(1);
        var completed = new java.util.concurrent.atomic.AtomicInteger();
        var references = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var failures = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(attempts);
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
            org.awaitility.Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(30))
                    .untilAtomic(completed, org.hamcrest.Matchers.equalTo(attempts));
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
        var sender =
                registration.register("concurrency-sender-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var receiver =
                registration.register("concurrency-receiver-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(senderWallet.getId(), new java.math.BigDecimal("100.00"));
        int attempts = 20;
        var start = new java.util.concurrent.CountDownLatch(1);
        var completed = new java.util.concurrent.atomic.AtomicInteger();
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        var unexpected = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(12);
        try {
            for (int i = 0; i < attempts; i++)
                executor.submit(() -> {
                    try {
                        start.await();
                        transferService.create(
                                sender.id(),
                                new com.wallettransfer.transfers.dto.CreateTransferRequest(
                                        receiverWallet.getId(),
                                        new java.math.BigDecimal("10.00"),
                                        com.wallettransfer.shared.money.Currency.NGN,
                                        "concurrent debit"));
                        successes.incrementAndGet();
                    } catch (com.wallettransfer.shared.exception.DomainException expected) {
                        rejected.incrementAndGet();
                    } catch (Throwable failure) {
                        unexpected.add(failure);
                    } finally {
                        completed.incrementAndGet();
                    }
                });
            start.countDown();
            org.awaitility.Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(30))
                    .untilAtomic(completed, org.hamcrest.Matchers.equalTo(attempts));
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
    void externalTransfersReserveSettleReleaseAndProcessSignedWebhookIdempotently() throws Exception {
        var owner = registration.register("external-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var wallet = walletRepository
                .findByOwnerIdAndCurrency(owner.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(wallet.getId(), new java.math.BigDecimal("100.00"));
        var successRequest = new com.wallettransfer.externaltransfers.dto.CreateExternalTransferRequest(
                new java.math.BigDecimal("30.00"),
                com.wallettransfer.shared.money.Currency.NGN,
                "SIM-SUCCESS",
                "external success");
        String successKey = "external-success-" + java.util.UUID.randomUUID();
        var accepted = externalTransferService.create(owner.id(), successKey, successRequest);
        assertThat(externalTransferService
                        .create(owner.id(), successKey, successRequest)
                        .reference())
                .isEqualTo(accepted.reference());
        assertThatThrownBy(() -> externalTransferService.create(
                        owner.id(),
                        successKey,
                        new com.wallettransfer.externaltransfers.dto.CreateExternalTransferRequest(
                                new java.math.BigDecimal("31.00"),
                                com.wallettransfer.shared.money.Currency.NGN,
                                "SIM-SUCCESS",
                                "external success")))
                .isInstanceOf(
                        com.wallettransfer.externaltransfers.exception.ExternalIdempotencyConflictException.class);
        java.util.UUID successId;
        String successProviderRequest;
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT id,provider_request_reference FROM external_transfers WHERE reference=?")) {
            q.setString(1, accepted.reference());
            try (var rows = q.executeQuery()) {
                rows.next();
                successId = (java.util.UUID) rows.getObject(1);
                successProviderRequest = rows.getString(2);
            }
        }
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT available_balance,ledger_balance FROM wallets WHERE id=?")) {
            q.setObject(1, wallet.getId());
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("70.00");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("100.00");
            }
        }
        externalCompletionService.start(successId);
        var providerSuccess =
                providerSimulatorService.submit(new com.wallettransfer.providersimulator.dto.SimulatorTransferRequest(
                        successProviderRequest, "SIM-SUCCESS", new java.math.BigDecimal("30.00"), "NGN"));
        externalCompletionService.successful(successId, providerSuccess.providerReference());
        externalCompletionService.successful(successId, providerSuccess.providerReference());
        var failed = externalTransferService.create(
                owner.id(),
                "external-fail-" + java.util.UUID.randomUUID(),
                new com.wallettransfer.externaltransfers.dto.CreateExternalTransferRequest(
                        new java.math.BigDecimal("20.00"),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "SIM-FAIL",
                        "external failure"));
        java.util.UUID failedId;
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement("SELECT id FROM external_transfers WHERE reference=?")) {
            q.setString(1, failed.reference());
            try (var rows = q.executeQuery()) {
                rows.next();
                failedId = (java.util.UUID) rows.getObject(1);
            }
        }
        externalCompletionService.start(failedId);
        externalCompletionService.failed(failedId, "PROVIDER_REJECTED");
        var uncertain = externalTransferService.create(
                owner.id(),
                "external-timeout-" + java.util.UUID.randomUUID(),
                new com.wallettransfer.externaltransfers.dto.CreateExternalTransferRequest(
                        new java.math.BigDecimal("10.00"),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "SIM-TIMEOUT-SUCCESS",
                        "uncertain transfer"));
        java.util.UUID uncertainId;
        String uncertainRequest;
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT id,provider_request_reference FROM external_transfers WHERE reference=?")) {
            q.setString(1, uncertain.reference());
            try (var rows = q.executeQuery()) {
                rows.next();
                uncertainId = (java.util.UUID) rows.getObject(1);
                uncertainRequest = rows.getString(2);
            }
        }
        externalCompletionService.start(uncertainId);
        assertThatThrownBy(() -> providerSimulatorService.submit(
                        new com.wallettransfer.providersimulator.dto.SimulatorTransferRequest(
                                uncertainRequest, "SIM-TIMEOUT-SUCCESS", new java.math.BigDecimal("10.00"), "NGN")))
                .isInstanceOf(com.wallettransfer.providersimulator.exception.SimulatedProviderTimeoutException.class);
        externalCompletionService.uncertain(uncertainId);
        String providerReference;
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT provider_transfer_reference FROM simulated_provider_transfers WHERE provider_request_reference=?")) {
            q.setString(1, uncertainRequest);
            try (var rows = q.executeQuery()) {
                rows.next();
                providerReference = rows.getString(1);
            }
        }
        String body = "{\"providerRequestReference\":\"" + uncertainRequest + "\",\"providerTransferReference\":\""
                + providerReference + "\",\"status\":\"SUCCESSFUL\"}";
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                "local-provider-webhook-secret-change-me".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256"));
        String signature = java.util.HexFormat.of()
                .formatHex(mac.doFinal((timestamp + "." + body).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThatThrownBy(
                        () -> providerWebhookService.process("simulator", "invalid-signature", timestamp, "00", body))
                .isInstanceOf(com.wallettransfer.providers.exception.InvalidWebhookSignatureException.class);
        assertThat(providerWebhookService.process("simulator", "webhook-" + uncertainId, timestamp, signature, body))
                .isTrue();
        assertThat(providerWebhookService.process("simulator", "webhook-" + uncertainId, timestamp, signature, body))
                .isFalse();
        String conflictingBody = body.replace("SUCCESSFUL", "FAILED");
        String conflictingSignature = java.util.HexFormat.of()
                .formatHex(mac.doFinal(
                        (timestamp + "." + conflictingBody).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> providerWebhookService.process(
                        "simulator", "webhook-" + uncertainId, timestamp, conflictingSignature, conflictingBody))
                .isInstanceOf(com.wallettransfer.providers.exception.WebhookReplayConflictException.class);
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT (SELECT available_balance FROM wallets WHERE id=?),(SELECT ledger_balance FROM wallets WHERE id=?),(SELECT count(*) FROM journal_transactions WHERE source_type='EXTERNAL_TRANSFER' AND source_reference IN (?,?)),(SELECT count(*) FROM provider_webhook_events WHERE provider_event_id=?),(SELECT count(*) FROM external_transfer_reservations WHERE status='RELEASED' AND external_transfer_id=?)")) {
            q.setObject(1, wallet.getId());
            q.setObject(2, wallet.getId());
            q.setString(3, accepted.reference());
            q.setString(4, uncertain.reference());
            q.setString(5, "webhook-" + uncertainId);
            q.setObject(6, failedId);
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("60.00");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("60.00");
                assertThat(rows.getInt(3)).isEqualTo(2);
                assertThat(rows.getInt(4)).isOne();
                assertThat(rows.getInt(5)).isOne();
            }
        }
        try (var connection = dataSource.getConnection();
                var q = connection.createStatement();
                var rows = q.executeQuery(
                        "SELECT sum(amount) FILTER(WHERE entry_type='DEBIT'),sum(amount) FILTER(WHERE entry_type='CREDIT') FROM journal_entries e JOIN journal_transactions j ON j.id=e.journal_transaction_id WHERE j.source_type='EXTERNAL_TRANSFER'")) {
            rows.next();
            assertThat(rows.getBigDecimal(1)).isEqualByComparingTo(rows.getBigDecimal(2));
        }
    }

    @Test
    void reconciliationResolvesTimeoutSuccessByStatusQueryWithoutRepeatingCreate() throws Exception {
        var owner = registration.register("reconcile-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var wallet = walletRepository
                .findByOwnerIdAndCurrency(owner.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(wallet.getId(), new java.math.BigDecimal("40.00"));
        var accepted = externalTransferService.create(
                owner.id(),
                "reconcile-key-" + java.util.UUID.randomUUID(),
                new com.wallettransfer.externaltransfers.dto.CreateExternalTransferRequest(
                        new java.math.BigDecimal("12.00"),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "SIM-TIMEOUT-SUCCESS",
                        "reconcile timeout"));
        java.util.UUID id;
        String request;
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT id,provider_request_reference FROM external_transfers WHERE reference=?")) {
            q.setString(1, accepted.reference());
            try (var rows = q.executeQuery()) {
                rows.next();
                id = (java.util.UUID) rows.getObject(1);
                request = rows.getString(2);
            }
        }
        externalCompletionService.start(id);
        assertThatThrownBy(() -> resilientProviderTransferService.create(
                        request, "SIM-TIMEOUT-SUCCESS", new java.math.BigDecimal("12.00"), "NGN"))
                .isInstanceOf(com.wallettransfer.providers.exception.UncertainProviderOutcomeException.class);
        externalCompletionService.uncertain(id, "read timeout");
        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        "UPDATE external_transfers SET next_reconciliation_at=CURRENT_TIMESTAMP WHERE id=?")) {
            update.setObject(1, id);
            update.executeUpdate();
        }
        reconciliationWorker.reconcile();
        try (var connection = dataSource.getConnection();
                var q = connection.prepareStatement(
                        "SELECT e.status,w.available_balance,w.ledger_balance,e.reconciliation_attempts,(SELECT count(*) FROM simulated_provider_transfers WHERE provider_request_reference=?),(SELECT count(*) FROM provider_reconciliation_attempts WHERE external_transfer_id=?) FROM external_transfers e JOIN wallets w ON w.id=e.sender_wallet_id WHERE e.id=?")) {
            q.setString(1, request);
            q.setObject(2, id);
            q.setObject(3, id);
            try (var rows = q.executeQuery()) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("SUCCESSFUL");
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("28.00");
                assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("28.00");
                assertThat(rows.getInt(4)).isOne();
                assertThat(rows.getInt(5)).isOne();
                assertThat(rows.getInt(6)).isOne();
            }
        }
    }

    @Test
    void successfulReversalIsIdempotentBalancedAuditedAndImmutable() throws Exception {
        var sender = registration.register("reversal-sender-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var receiver =
                registration.register("reversal-receiver-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var admin = registration.register("reversal-admin-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(senderWallet.getId(), new java.math.BigDecimal("100.00"));
        var original = transferService.create(
                sender.id(),
                new com.wallettransfer.transfers.dto.CreateTransferRequest(
                        receiverWallet.getId(),
                        new java.math.BigDecimal("25.00"),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "reversible transfer"));
        String key = "reversal-key-" + java.util.UUID.randomUUID();
        var request = new com.wallettransfer.reversals.dto.CreateReversalRequest("confirmed duplicate");
        var reversal = transferReversalService.reverse(admin.id(), original.reference(), key, request);
        assertThat(transferReversalService
                        .reverse(admin.id(), original.reference(), key, request)
                        .reversalReference())
                .isEqualTo(reversal.reversalReference());
        assertThatThrownBy(() -> transferReversalService.reverse(
                        admin.id(), original.reference(), "different-key-" + java.util.UUID.randomUUID(), request))
                .isInstanceOf(com.wallettransfer.reversals.exception.ReversalAlreadyExistsException.class);
        assertThatThrownBy(() -> transferReversalService.reverse(
                        admin.id(),
                        original.reference(),
                        key,
                        new com.wallettransfer.reversals.dto.CreateReversalRequest("different reason")))
                .isInstanceOf(com.wallettransfer.reversals.exception.ReversalIdempotencyConflictException.class);
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
        var sender =
                registration.register("poor-reversal-sender-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var receiver =
                registration.register("poor-reversal-receiver-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var third =
                registration.register("poor-reversal-third-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var admin =
                registration.register("poor-reversal-admin-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var thirdWallet = walletRepository
                .findByOwnerIdAndCurrency(third.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(senderWallet.getId(), new java.math.BigDecimal("50.00"));
        var original = transferService.create(
                sender.id(),
                new com.wallettransfer.transfers.dto.CreateTransferRequest(
                        receiverWallet.getId(),
                        new java.math.BigDecimal("25.00"),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "cannot reverse"));
        transferService.create(
                receiver.id(),
                new com.wallettransfer.transfers.dto.CreateTransferRequest(
                        thirdWallet.getId(),
                        new java.math.BigDecimal("20.00"),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "receiver spent funds"));
        assertThatThrownBy(() -> transferReversalService.reverse(
                        admin.id(),
                        original.reference(),
                        "insufficient-reversal-" + java.util.UUID.randomUUID(),
                        new com.wallettransfer.reversals.dto.CreateReversalRequest("attempt reversal")))
                .isInstanceOf(com.wallettransfer.wallets.exception.WalletTransferRejectedException.class);
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
        String email = "reversal-customer-" + java.util.UUID.randomUUID() + "@example.com",
                password = "correct horse battery staple";
        var customer = registration.register(email, "hash");
        String access = issueAccessToken(customer.id(), email, password);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/transfers/TRF-NOT-RELEVANT/reverse")
                        .header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", "forbidden-reversal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"not authorized\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isForbidden());
    }

    @Test
    void concurrentReversalRequestsProduceOneCompensatingJournal() throws Exception {
        var sender = registration.register(
                "concurrent-reversal-sender-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var receiver = registration.register(
                "concurrent-reversal-receiver-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var admin = registration.register(
                "concurrent-reversal-admin-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(senderWallet.getId(), new java.math.BigDecimal("40.00"));
        var original = transferService.create(
                sender.id(),
                new com.wallettransfer.transfers.dto.CreateTransferRequest(
                        receiverWallet.getId(),
                        new java.math.BigDecimal("15.00"),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "concurrent reversal original"));
        int attempts = 10;
        var start = new java.util.concurrent.CountDownLatch(1);
        var done = new java.util.concurrent.atomic.AtomicInteger();
        var successful = new java.util.concurrent.atomic.AtomicInteger();
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        var unexpected = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(attempts);
        try {
            for (int i = 0; i < attempts; i++) {
                String key = "concurrent-reversal-" + i + "-" + java.util.UUID.randomUUID();
                executor.submit(() -> {
                    try {
                        start.await();
                        transferReversalService.reverse(
                                admin.id(),
                                original.reference(),
                                key,
                                new com.wallettransfer.reversals.dto.CreateReversalRequest(
                                        "concurrent operations reversal"));
                        successful.incrementAndGet();
                    } catch (com.wallettransfer.shared.exception.DomainException expected) {
                        rejected.incrementAndGet();
                    } catch (Throwable failure) {
                        unexpected.add(failure);
                    } finally {
                        done.incrementAndGet();
                    }
                });
            }
            start.countDown();
            org.awaitility.Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(30))
                    .untilAtomic(done, org.hamcrest.Matchers.equalTo(attempts));
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
        var owner = registration.register(
                "financial-reconciliation-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var operator = registration.register(
                "financial-reconciliation-operator-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var wallet = walletRepository
                .findByOwnerIdAndCurrency(owner.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(wallet.getId(), new java.math.BigDecimal("50.00"));

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

        java.util.UUID caseId;
        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement(
                        "SELECT id, status, severity, count(*) OVER() FROM reconciliation_cases "
                                + "WHERE case_key = ?")) {
            query.setString(1, "WALLET_PROJECTION:" + wallet.getId());
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                caseId = (java.util.UUID) rows.getObject(1);
                assertThat(rows.getString(2)).isEqualTo("OPEN");
                assertThat(rows.getString(3)).isEqualTo("HIGH");
                assertThat(rows.getInt(4)).isOne();
            }
        }

        String key = "projection-repair-" + java.util.UUID.randomUUID();
        var request = new com.wallettransfer.reconciliation.dto.RepairWalletProjectionRequest(
                "Restore the materialized wallet balance from immutable ledger entries");
        var repair = reconciliationRepairService.repairWallet(operator.id(), caseId, key, request);
        assertThat(reconciliationRepairService
                        .repairWallet(operator.id(), caseId, key, request)
                        .repairId())
                .isEqualTo(repair.repairId());
        assertThatThrownBy(() -> reconciliationRepairService.repairWallet(
                        operator.id(),
                        caseId,
                        key,
                        new com.wallettransfer.reconciliation.dto.RepairWalletProjectionRequest(
                                "Different repair request")))
                .isInstanceOf(com.wallettransfer.reconciliation.exception.ReconciliationRepairConflictException.class);

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
        var sender =
                registration.register("bad-transfer-sender-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var receiver =
                registration.register("bad-transfer-receiver-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var operator =
                registration.register("bad-transfer-operator-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        java.util.UUID transferId = java.util.UUID.randomUUID();
        String reference = "TRF-BROKEN-" + java.util.UUID.randomUUID();
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
        String email = "reconciliation-customer-" + java.util.UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";
        var customer = registration.register(email, "hash");
        String access = issueAccessToken(customer.id(), email, password);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/admin/reconciliation/runs")
                        .header("Authorization", "Bearer " + access))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isForbidden());
    }

    @Test
    void operationalMetricsExposeCommittedTransfersAndRealOutboxBacklog() throws Exception {
        var sender = registration.register("metrics-sender-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var receiver =
                registration.register("metrics-receiver-" + java.util.UUID.randomUUID() + "@example.com", "hash");
        var senderWallet = walletRepository
                .findByOwnerIdAndCurrency(sender.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        var receiverWallet = walletRepository
                .findByOwnerIdAndCurrency(receiver.id(), com.wallettransfer.shared.money.Currency.NGN)
                .orElseThrow();
        fundWallet(senderWallet.getId(), new java.math.BigDecimal("20.00"));
        double before = meterRegistry
                .counter("wallet.transfers", "type", "internal", "outcome", "successful")
                .count();

        transferService.create(
                sender.id(),
                new com.wallettransfer.transfers.dto.CreateTransferRequest(
                        receiverWallet.getId(),
                        new java.math.BigDecimal("5.00"),
                        com.wallettransfer.shared.money.Currency.NGN,
                        "observable transfer"));
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

    private String issueAccessToken(java.util.UUID userId, String email, String password) throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")) {
            statement.setString(
                    1, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(password));
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
        String login = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(login)
                .get("accessToken")
                .asText();
    }

    private void fundWallet(java.util.UUID walletId, java.math.BigDecimal amount) throws Exception {
        var walletAccount = ledgerService.getAccountForWallet(walletId);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            java.util.UUID journal = java.util.UUID.randomUUID(), settlement;
            try (var q = connection.createStatement();
                    var row = q.executeQuery(
                            "SELECT id FROM ledger_accounts WHERE account_code='PLATFORM-NGN-SETTLEMENT'")) {
                row.next();
                settlement = (java.util.UUID) row.getObject(1);
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
                e.setObject(1, java.util.UUID.randomUUID());
                e.setObject(2, journal);
                e.setObject(3, settlement);
                e.setShort(4, (short) 1);
                e.setString(5, "DEBIT");
                e.setBigDecimal(6, amount);
                e.executeUpdate();
                e.setObject(1, java.util.UUID.randomUUID());
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
