package com.wallettransfer.shared.money;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.scale() > currency.scale()) {
            throw new IllegalArgumentException("amount exceeds the currency scale");
        }
        amount = amount.setScale(currency.scale());
    }
}
