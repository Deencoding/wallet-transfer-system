package com.wallettransfer.reconciliation.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class ReconciliationCaseNotFoundException extends DomainException {
    public ReconciliationCaseNotFoundException() {
        super(DomainErrorCode.RECONCILIATION_CASE_NOT_FOUND, "Reconciliation case was not found");
    }
}
