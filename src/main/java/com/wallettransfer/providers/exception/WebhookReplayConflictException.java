package com.wallettransfer.providers.exception;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;

public final class WebhookReplayConflictException extends DomainException {
    public WebhookReplayConflictException() {
        super(DomainErrorCode.WEBHOOK_REPLAY_CONFLICT, "Webhook event identifier was reused with different content");
    }
}
