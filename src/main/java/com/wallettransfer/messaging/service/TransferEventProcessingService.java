package com.wallettransfer.messaging.service;

import com.wallettransfer.audit.service.AuditService;
import com.wallettransfer.inbox.repository.ConsumedEventRepository;
import com.wallettransfer.messaging.dto.TransferCompletedMessage;
import com.wallettransfer.messaging.exception.InvalidEventException;
import com.wallettransfer.notifications.service.NotificationService;
import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.wallets.service.WalletService;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferEventProcessingService {
    public static final String CONSUMER = "transfer-projections-v1";
    private final ConsumedEventRepository inbox;
    private final NotificationService notifications;
    private final AuditService audit;
    private final WalletService wallets;
    private final Clock clock;

    public TransferEventProcessingService(
            ConsumedEventRepository inbox,
            NotificationService notifications,
            AuditService audit,
            WalletService wallets,
            Clock clock) {
        this.inbox = inbox;
        this.notifications = notifications;
        this.audit = audit;
        this.wallets = wallets;
        this.clock = clock;
    }

    @Transactional
    public boolean process(
            UUID eventId,
            String eventType,
            int version,
            UUID aggregateId,
            String correlationId,
            TransferCompletedMessage message) {
        validate(eventId, eventType, version, aggregateId, message);
        Instant now = clock.instant();
        if (inbox.claim(UUID.randomUUID(), eventId, CONSUMER, eventType, version, aggregateId, now) == 0) {
            return false;
        }
        UUID sender = wallets.getOwnerId(message.senderWalletId());
        UUID receiver = wallets.getOwnerId(message.receiverWalletId());
        notifications.transferCompleted(
                eventId,
                message.transferId(),
                sender,
                receiver,
                message.reference(),
                message.amount(),
                message.currency());
        audit.transferCompleted(
                eventId,
                sender,
                message.transferId(),
                correlationId,
                message.reference(),
                message.amount(),
                message.currency(),
                message.completedAt());
        inbox.complete(CONSUMER, eventId, clock.instant());
        return true;
    }

    private void validate(UUID eventId, String type, int version, UUID aggregateId, TransferCompletedMessage message) {
        if (eventId == null || aggregateId == null || message == null)
            throw new InvalidEventException("Required event identity is missing");
        if (!"TransferCompleted".equals(type) || version != 1)
            throw new InvalidEventException("Unsupported event type or version");
        if (message.transferId() == null
                || !message.transferId().equals(aggregateId)
                || message.senderWalletId() == null
                || message.receiverWalletId() == null
                || message.senderWalletId().equals(message.receiverWalletId())
                || message.reference() == null
                || message.reference().isBlank()
                || message.completedAt() == null) throw new InvalidEventException("Transfer event payload is invalid");
        try {
            if (new BigDecimal(message.amount()).signum() <= 0) {
                throw new NumberFormatException("non-positive");
            }
            Currency.valueOf(message.currency());
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventException("Transfer money fields are invalid", exception);
        }
    }
}
