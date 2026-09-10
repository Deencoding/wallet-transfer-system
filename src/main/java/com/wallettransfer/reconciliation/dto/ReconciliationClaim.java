package com.wallettransfer.reconciliation.dto;

import java.util.UUID;

public record ReconciliationClaim(UUID transferId, String providerRequestReference, int attemptNumber) {}
