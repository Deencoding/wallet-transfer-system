package com.wallettransfer.providersimulator.dto;

public record SimulatorTransferResponse(
        String requestReference, String providerReference, String status, String failureReason) {}
