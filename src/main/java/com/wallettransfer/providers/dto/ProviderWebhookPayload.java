package com.wallettransfer.providers.dto;

public record ProviderWebhookPayload(
        String providerRequestReference, String providerTransferReference, String status, String failureReason) {}
