package com.wallettransfer.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesAnExactAmountToTheCurrencyScale() {
        BigDecimal inputAmount = new BigDecimal("100");
        Money money = new Money(inputAmount, Currency.NGN);

        assertThat(money.amount()).isEqualByComparingTo("100.00");
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    void rejectsImplicitRounding() {
        BigDecimal inputAmount = new BigDecimal("10.001");
        assertThatThrownBy(() -> new Money(inputAmount, Currency.NGN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency scale");
    }
}
