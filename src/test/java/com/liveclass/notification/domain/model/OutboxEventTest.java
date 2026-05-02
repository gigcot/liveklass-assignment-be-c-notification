package com.liveclass.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private static final UUID NOTIFICATION_ID = UUID.randomUUID();
    private static final OutboxPayload PAYLOAD = new OutboxPayload(
            NOTIFICATION_ID, UUID.randomUUID(), Channel.IN_APP, "안녕하세요"
    );

    @Test
    @DisplayName("생성 시 PENDING 상태로 초기화")
    void create_initializes_with_pending_status() {
        OutboxEvent event = OutboxEvent.create(NOTIFICATION_ID, PAYLOAD, LocalDateTime.now());

        assertThat(event.getId()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getNotificationId()).isEqualTo(NOTIFICATION_ID);
        assertThat(event.getPayload()).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("markRelayed 호출 시 RELAYED로 전이")
    void markRelayed_changes_status_to_relayed() {
        OutboxEvent event = OutboxEvent.create(NOTIFICATION_ID, PAYLOAD, LocalDateTime.now());

        event.markRelayed();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.RELAYED);
    }

    @Test
    @DisplayName("scheduledAt이 null이면 즉시 처리 대상")
    void create_with_null_scheduledAt() {
        OutboxEvent event = OutboxEvent.create(NOTIFICATION_ID, PAYLOAD, null);

        assertThat(event.getScheduledAt()).isNull();
    }

    @Test
    @DisplayName("payload에 notificationId 포함")
    void payload_contains_notificationId() {
        OutboxEvent event = OutboxEvent.create(NOTIFICATION_ID, PAYLOAD, null);

        assertThat(event.getPayload().notificationId()).isEqualTo(NOTIFICATION_ID);
    }
}
