package com.liveklass.notification.application.service;

import com.liveklass.notification.application.port.inbound.RetryNotificationUseCase;
import com.liveklass.notification.application.port.outbound.NotificationRegistrationPort;
import com.liveklass.notification.application.port.outbound.NotificationRepository;
import com.liveklass.notification.domain.exception.NotificationNotFoundException;
import com.liveklass.notification.domain.model.OutboxEvent;
import com.liveklass.notification.domain.model.OutboxPayload;
import com.liveklass.notification.domain.model.SendStatus;
import com.liveklass.notification.domain.model.UserNotification;

import java.util.List;
import java.util.UUID;

public class RetryNotificationService implements RetryNotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationRegistrationPort registrationPort;

    public RetryNotificationService(NotificationRepository notificationRepository,
                                    NotificationRegistrationPort registrationPort) {
        this.notificationRepository = notificationRepository;
        this.registrationPort = registrationPort;
    }

    @Override
    public List<UserNotification> findFailed() {
        return notificationRepository.findBySendStatus(SendStatus.FAILED);
    }

    @Override
    public void retryManually(UUID id) {
        UserNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));

        notification.retryManually();

        OutboxEvent outboxEvent = OutboxEvent.create(
                notification.getId(),
                new OutboxPayload(notification.getId(), notification.getUserId(),
                        notification.getChannel(),
                        notification.getRenderedTitle() + "\n" + notification.getRenderedBody()),
                null
        );

        registrationPort.save(notification, outboxEvent);
    }
}
