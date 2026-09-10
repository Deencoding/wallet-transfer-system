package com.wallettransfer.authentication.security;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String hash(String rawPassword) {
        validate(rawPassword);
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return acceptable(rawPassword) && encoder.matches(rawPassword, passwordHash);
    }

    public void validate(String password) {
        if (!acceptable(password)) {
            throw new IllegalArgumentException("password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
    }

    private boolean acceptable(String password) {
        return password != null
                && password.length() >= 12
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && !password.isBlank()
                && password.codePoints().noneMatch(Character::isISOControl);
    }
}
