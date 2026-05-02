package com.liveklass.notification.adapter.outbound.persistence;

import com.liveklass.notification.application.port.outbound.NotificationRegistrationPort;
import com.liveklass.notification.domain.model.*;
import com.liveklass.notification.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(NotificationRegistrationAdapter.class)
class NotificationRegistrationAdapterTest {

    @Autowired
    private NotificationRegistrationPort registrationPort;

    @Autowired
    private NotificationJpaRepository notificationRepo;

    @Autowired
    private OutboxJpaRepository outboxRepo;

    @Test
    @DisplayName("UserNotification과 OutboxEvent가 하나의 트랜잭션으로 저장된다")
    void saves_notification_and_outbox_atomically() {
        UserNotification notification = buildNotification();
        OutboxEvent outboxEvent = buildOutboxEvent(notification);

        registrationPort.save(notification, outboxEvent);

        assertThat(notificationRepo.findById(notification.getId())).isPresent();
        assertThat(outboxRepo.findById(outboxEvent.getId())).isPresent();
    }

    @Test
    @DisplayName("동일 eventId + userId 중복 저장 시 예외 발생")
    void throws_on_duplicate_event_and_user() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        UserNotification first = buildNotification(userId, eventId);
        registrationPort.save(first, buildOutboxEvent(first));

        UserNotification duplicate = buildNotification(userId, eventId);
        assertThatThrownBy(() -> {
            registrationPort.save(duplicate, buildOutboxEvent(duplicate));
            notificationRepo.flush();
        }).isInstanceOf(Exception.class);
    }

    private UserNotification buildNotification() {
        return buildNotification(UUID.randomUUID(), UUID.randomUUID());
    }

    private UserNotification buildNotification(UUID userId, UUID eventId) {
        return UserNotification.create(
                userId, eventId, UUID.randomUUID(), Channel.IN_APP,
                new ReferenceData(Map.of()), null, null,
                LocalDateTime.now()
        );
    }

    private OutboxEvent buildOutboxEvent(UserNotification notification) {
        OutboxPayload payload = new OutboxPayload(
                notification.getId(), notification.getUserId(),
                notification.getChannel(), "테스트 메시지"
        );
        return OutboxEvent.create(notification.getId(), payload, LocalDateTime.now());
    }
}
