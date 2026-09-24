package com.wallettransfer.users.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressTest {
    @Test
    void normalizesEmailIdentity() {
        EmailAddress email = new EmailAddress("  Customer@Example.COM ");
        assertThat(email.value()).isEqualTo("customer@example.com");
    }

    @Test
    void rejectsMalformedEmail() {
        assertThatThrownBy(() -> new EmailAddress("not-an-email")).isInstanceOf(IllegalArgumentException.class);
    }
}
