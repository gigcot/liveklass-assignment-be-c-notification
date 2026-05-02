package com.liveklass.notification.application.service;

import com.liveklass.notification.domain.exception.DuplicateNotificationException;
import com.liveklass.notification.domain.exception.NotificationNotFoundException;
import com.liveklass.notification.domain.exception.TemplateNotFoundException;
import com.liveklass.notification.domain.model.*;
import com.liveklass.notification.application.port.inbound.GetNotificationUseCase;
import com.liveklass.notification.application.port.inbound.MarkReadUseCase;
import com.liveklass.notification.application.port.inbound.RegisterNotificationUseCase;
import com.liveklass.notification.application.port.outbound.NotificationRegistrationPort;
import com.liveklass.notification.application.port.outbound.NotificationRepository;
import com.liveklass.notification.application.port.outbound.TemplateRepository;
import com.liveklass.notification.domain.model.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NotificationService implements RegisterNotificationUseCase, GetNotificationUseCase, MarkReadUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationRegistrationPort registrationPort;
    private final TemplateRepository templateRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationRegistrationPort registrationPort,
                               TemplateRepository templateRepository) {
        this.notificationRepository = notificationRepository;
        this.registrationPort = registrationPort;
        this.templateRepository = templateRepository;
    }

    @Override
    public UUID register(UUID userId, UUID eventId, UUID templateId,
                         NotificationType notificationType, Channel channel,
                         Map<String, String> referenceData, LocalDateTime scheduledAt) {

        if (notificationRepository.existsByEventIdAndUserId(eventId, userId)) {
            throw new DuplicateNotificationException(eventId, userId);
        }

        NotificationTemplate template = resolveTemplate(templateId, notificationType, channel);
        ReferenceData data = new ReferenceData(referenceData != null ? referenceData : Map.of());
        String renderedTitle = template.renderTitle(data);
        String renderedBody = template.renderBody(data);

        UserNotification notification = UserNotification.create(
                userId, eventId, template.getId(), channel, data, renderedTitle, renderedBody, scheduledAt
        );
        OutboxEvent outboxEvent = OutboxEvent.create(
                notification.getId(),
                new OutboxPayload(notification.getId(), userId, channel, renderedTitle + "\n" + renderedBody),
                scheduledAt
        );

        return registrationPort.save(notification, outboxEvent);
    }

    private NotificationTemplate resolveTemplate(UUID templateId, NotificationType type, Channel channel) {
        if (templateId != null) {
            return templateRepository.findById(templateId)
                    .orElseThrow(() -> TemplateNotFoundException.byId(templateId));
        }
        return templateRepository.findLatestByTypeAndChannel(type, channel)
                .orElseThrow(() -> TemplateNotFoundException.byTypeAndChannel(type, channel));
    }

    @Override
    public UserNotification getById(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    @Override
    public void markRead(UUID id) {
        UserNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (notification.getReadAt() != null) {
            return;
        }
        notification.markRead();
        notificationRepository.save(notification);
    }

    @Override
    public List<UserNotification> getByUserId(UUID userId, Boolean unreadOnly) {
        if (unreadOnly == null) {
            return notificationRepository.findByUserId(userId);
        }
        if (unreadOnly) {
            return notificationRepository.findByUserIdAndReadAtIsNull(userId);
        }
        return notificationRepository.findByUserIdAndReadAtIsNotNull(userId);
    }
}
