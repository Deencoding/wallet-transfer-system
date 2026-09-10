package com.wallettransfer.notifications.repository;

import com.wallettransfer.notifications.model.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    long countByEventId(UUID eventId);
}
