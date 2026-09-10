package com.wallettransfer.authentication.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {
    private final PasswordService passwords = new PasswordService();

    @Test
    void hashesAndVerifiesAPassword() {
        String hash = passwords.hash("correct horse battery staple");
        assertThat(hash).doesNotContain("correct horse battery staple");
        assertThat(passwords.matches("correct horse battery staple", hash)).isTrue();
    }

    @Test
    void rejectsPasswordsBeyondBcryptsUtf8Limit() {
        assertThatThrownBy(() -> passwords.hash("€".repeat(25)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 UTF-8 bytes");
    }

    @Test
    void rejectsControlCharacters() {
        assertThatThrownBy(() -> passwords.hash("valid length\npassword")).isInstanceOf(IllegalArgumentException.class);
        assertThat(passwords.matches("valid length\npassword", passwords.hash("correct horse battery staple")))
                .isFalse();
    }
}
