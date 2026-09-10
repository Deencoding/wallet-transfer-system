package com.wallettransfer.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesAnExactAmountToTheCurrencyScale() {
        Money money = new Money(new BigDecimal("100"), Currency.NGN);

        assertThat(money.amount()).isEqualByComparingTo("100.00");
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    void rejectsImplicitRounding() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.001"), Currency.NGN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency scale");
    }
}
