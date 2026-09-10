package com.wallettransfer.transfers.dto;

import com.wallettransfer.shared.money.Currency;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID receiverWalletId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull Currency currency,
        @Size(max = 255) String description) {}
