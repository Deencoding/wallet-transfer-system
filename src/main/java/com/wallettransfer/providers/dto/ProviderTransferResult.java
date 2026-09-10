package com.wallettransfer.providers.dto;

public record ProviderTransferResult(
        String requestReference, String providerReference, String status, String failureReason) {}
