package com.wallettransfer.externaltransfers.model;

public enum ExternalTransferStatus {
    PENDING,
    PROCESSING,
    PENDING_PROVIDER_CONFIRMATION,
    MANUAL_REVIEW,
    SUCCESSFUL,
    FAILED
}
