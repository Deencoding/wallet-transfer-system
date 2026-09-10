package com.wallettransfer.reconciliation.dto;

import jakarta.validation.constraints.*;

public record RepairWalletProjectionRequest(@NotBlank @Size(max = 1000) String reason) {}
