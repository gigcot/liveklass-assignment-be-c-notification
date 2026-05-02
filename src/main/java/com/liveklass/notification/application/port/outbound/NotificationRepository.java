package com.liveklass.notification.application.port.outbound;

import com.liveklass.notification.domain.model.SendStatus;
import com.liveklass.notification.domain.model.UserNotification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    UserNotification save(UserNotification notification);

    Optional<UserNotification> findById(UUID id);

    Optional<UserNotification> findByIdForUpdate(UUID id);

    List<UserNotification> findByUserId(UUID userId);

    List<UserNotification> findByUserIdAndReadAtIsNull(UUID userId);

    List<UserNotification> findByUserIdAndReadAtIsNotNull(UUID userId);

    List<UserNotification> findBySendStatus(SendStatus status);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);
}
