package com.wallettransfer.notifications.service;

import com.wallettransfer.notifications.model.*;
import com.wallettransfer.notifications.repository.NotificationRepository;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void transferCompleted(
            UUID eventId,
            UUID transferId,
            UUID senderId,
            UUID receiverId,
            String reference,
            String amount,
            String currency) {
        Instant now = clock.instant();
        Notification senderNotification = new Notification(
                UUID.randomUUID(),
                eventId,
                senderId,
                transferId,
                NotificationType.TRANSFER_SENT,
                "Transfer successful",
                "Transfer " + reference + " of " + currency + " " + amount + " was sent successfully",
                now);
        repository.save(senderNotification);
        Notification receiverNotification = new Notification(
                UUID.randomUUID(),
                eventId,
                receiverId,
                transferId,
                NotificationType.TRANSFER_RECEIVED,
                "Transfer received",
                "Transfer " + reference + " of " + currency + " " + amount + " was received",
                now);
        repository.save(receiverNotification);
    }
}
