package com.wallettransfer.notifications.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {}

    public Notification(
            UUID id,
            UUID eventId,
            UUID userId,
            UUID transferId,
            NotificationType type,
            String title,
            String message,
            Instant now) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.transferId = transferId;
        this.type = type;
        this.title = title;
        this.message = message;
        status = NotificationStatus.PENDING;
        createdAt = now;
    }
}
