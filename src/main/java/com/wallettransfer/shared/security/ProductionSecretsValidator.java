package com.wallettransfer.shared.security;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public final class ProductionSecretsValidator implements ApplicationRunner {

    private static final List<String> UNSAFE_VALUES = List.of("password", "secret", "changeme", "development-only");
    private final Environment environment;

    public ProductionSecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireConfigured("JWT_PUBLIC_KEY");
        requireConfigured("JWT_PRIVATE_KEY");
        requireStrongSecret("DB_PASSWORD", 16);
        requireStrongSecret("SECURITY_AUDIT_PEPPER", 32);
    }

    private void requireConfigured(String name) {
        if (!StringUtils.hasText(environment.getProperty(name))) {
            throw new IllegalStateException(name + " must be supplied externally in production");
        }
    }

    private void requireStrongSecret(String name, int minimumLength) {
        String value = environment.getProperty(name);
        boolean unsafe = !StringUtils.hasText(value)
                || value.length() < minimumLength
                || UNSAFE_VALUES.stream().anyMatch(marker -> value.toLowerCase().contains(marker));
        if (unsafe) {
            throw new IllegalStateException(name + " must be supplied as a strong external secret in production");
        }
    }
}
