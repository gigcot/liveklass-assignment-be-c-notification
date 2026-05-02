package com.liveclass.notification.application.service;

import com.liveclass.notification.application.port.inbound.UpdateNotificationStatusUseCase;
import com.liveclass.notification.application.port.outbound.NotificationRepository;
import com.liveclass.notification.domain.exception.NotificationNotFoundException;
import com.liveclass.notification.domain.model.FailureReason;
import com.liveclass.notification.domain.model.UserNotification;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class UpdateNotificationStatusService implements UpdateNotificationStatusUseCase {

    private final NotificationRepository notificationRepository;

    public UpdateNotificationStatusService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public void markQueued(UUID id) {
        UserNotification notification = notificationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notification.markQueued();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public boolean claim(UUID id) {
        UserNotification notification = notificationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        boolean claimed = notification.claim();
        if (claimed) {
            notificationRepository.save(notification);
        }
        return claimed;
    }

    @Override
    public void markSent(UUID id) {
        UserNotification notification = findOrThrow(id);
        notification.markSent();
        notificationRepository.save(notification);
    }

    @Override
    public boolean markFailed(UUID id, FailureReason reason) {
        UserNotification notification = findOrThrow(id);
        notification.markFailed(reason);
        notificationRepository.save(notification);
        return notification.getRetryInfo().canRetry();
    }

    private UserNotification findOrThrow(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }
}
