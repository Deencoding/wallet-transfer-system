package com.wallettransfer.reconciliation.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class UnsafeRepairException extends DomainException {
    public UnsafeRepairException(String message) {
        super(DomainErrorCode.UNSAFE_RECONCILIATION_REPAIR, message);
    }
}
