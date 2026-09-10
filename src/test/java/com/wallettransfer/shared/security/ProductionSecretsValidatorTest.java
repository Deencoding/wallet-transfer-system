package com.wallettransfer.shared.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecretsValidatorTest {

    @Test
    void rejectsMissingProductionSecrets() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(new MockEnvironment());

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PUBLIC_KEY");
    }

    @Test
    void acceptsStrongExternalSecrets() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_PUBLIC_KEY", "externally-provided-public-key")
                .withProperty("JWT_PRIVATE_KEY", "externally-provided-private-key")
                .withProperty("DB_PASSWORD", "a5ec79cb49684386a9156d594be3a809");
        environment.withProperty("PROVIDER_WEBHOOK_SECRET", "2e4da4fa551449fb8f812650fe31a9b6");
        environment.withProperty("SECURITY_AUDIT_PEPPER", "731bc479b04b4c9e9530132ad2be7650");
        ProductionSecretsValidator validator = new ProductionSecretsValidator(environment);

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }
}
