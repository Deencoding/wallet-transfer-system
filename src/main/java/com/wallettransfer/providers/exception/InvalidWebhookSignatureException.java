package com.wallettransfer.providers.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class InvalidWebhookSignatureException extends DomainException {
    public InvalidWebhookSignatureException() {
        super(DomainErrorCode.INVALID_WEBHOOK_SIGNATURE, "Webhook authentication failed");
    }
}
