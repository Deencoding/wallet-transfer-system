package com.wallettransfer.reconciliation.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class ReconciliationAlreadyRunningException extends DomainException {
    public ReconciliationAlreadyRunningException() {
        super(DomainErrorCode.RECONCILIATION_ALREADY_RUNNING, "A financial reconciliation run is already active");
    }
}
