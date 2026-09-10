package com.wallettransfer.providersimulator.dto;

import java.math.BigDecimal;

public record SimulatorTransferRequest(
        String requestReference, String beneficiaryToken, BigDecimal amount, String currency) {}
