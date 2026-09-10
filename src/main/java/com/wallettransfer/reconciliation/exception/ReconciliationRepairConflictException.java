package com.wallettransfer.reconciliation.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class ReconciliationRepairConflictException extends DomainException {
    public ReconciliationRepairConflictException() {
        super(
                DomainErrorCode.RECONCILIATION_REPAIR_CONFLICT,
                "Idempotency key was already used for another repair request");
    }
}
