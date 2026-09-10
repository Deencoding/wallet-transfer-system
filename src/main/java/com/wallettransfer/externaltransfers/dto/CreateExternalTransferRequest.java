package com.wallettransfer.externaltransfers.dto;

import com.wallettransfer.shared.money.Currency;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateExternalTransferRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull Currency currency,
        @NotBlank @Size(max = 100) String beneficiaryToken,
        @Size(max = 255) String description) {}
