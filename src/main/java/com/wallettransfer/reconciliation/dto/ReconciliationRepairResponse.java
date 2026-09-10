package com.wallettransfer.reconciliation.dto;

import java.util.UUID;

public record ReconciliationRepairResponse(UUID repairId, UUID caseId, String status) {}
