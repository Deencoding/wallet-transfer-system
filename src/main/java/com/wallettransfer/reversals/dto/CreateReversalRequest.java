package com.wallettransfer.reversals.dto;

import jakarta.validation.constraints.*;

public record CreateReversalRequest(@NotBlank @Size(max = 500) String reason) {}
