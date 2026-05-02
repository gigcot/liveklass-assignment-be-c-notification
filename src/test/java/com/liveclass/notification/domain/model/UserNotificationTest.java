package com.liveclass.notification.domain.model;

import com.liveclass.notification.domain.exception.InvalidStatusTransitionException;
import com.liveclass.notification.domain.model.FailureReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserNotificationTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    private UserNotification createNotification() {
        return UserNotification.create(
                USER_ID,
                EVENT_ID,
                TEMPLATE_ID,
                Channel.IN_APP,
                new ReferenceData(Map.of("courseName", "Spring Boot")),
                null, null,
                null
        );
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("생성 시 PENDING 상태")
        void shouldCreateWithPendingStatus() {
            UserNotification notification = createNotification();

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.PENDING);
            assertThat(notification.getRetryInfo().getCount()).isZero();
            assertThat(notification.getReadAt()).isNull();
        }

        @Test
        @DisplayName("scheduledAt이 있으면 예약 발송")
        void shouldCreateWithScheduledAt() {
            LocalDateTime scheduled = LocalDateTime.now().plusDays(1);
            UserNotification notification = UserNotification.create(
                    USER_ID, EVENT_ID, TEMPLATE_ID, Channel.IN_APP,
                    new ReferenceData(Map.of()), null, null, scheduled
            );

            assertThat(notification.getScheduledAt()).isEqualTo(scheduled);
            assertThat(notification.isReadyToSend()).isFalse();
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("PENDING → QUEUED")
        void shouldTransitionToQueued() {
            UserNotification notification = createNotification();
            notification.markQueued();

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.QUEUED);
        }

        @Test
        @DisplayName("QUEUED → SENDING → SENT")
        void shouldTransitionToSent() {
            UserNotification notification = createNotification();
            notification.markQueued();
            notification.claim();
            notification.markSent();

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.SENT);
        }

        @Test
        @DisplayName("SENDING이 아닌 상태에서 SENT 전이 시 예외")
        void shouldThrowWhenMarkSentFromNonSending() {
            UserNotification notification = createNotification();

            assertThatThrownBy(notification::markSent)
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 QUEUED 전이 시 예외")
        void shouldThrowWhenMarkQueuedFromNonPending() {
            UserNotification notification = createNotification();
            notification.markQueued();

            assertThatThrownBy(notification::markQueued)
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Nested
    @DisplayName("재시도")
    class Retry {

        @Test
        @DisplayName("3회 미만 실패 시 상태 유지")
        void shouldKeepStatusWhenRetryAvailable() {
            UserNotification notification = createNotification();
            notification.markQueued();
            notification.markFailed(FailureReason.TIMEOUT);

            assertThat(notification.getSendStatus()).isNotEqualTo(SendStatus.FAILED);
            assertThat(notification.getRetryInfo().canRetry()).isTrue();
        }

        @Test
        @DisplayName("3회 실패 시 FAILED")
        void shouldMarkFailedAfterMaxRetry() {
            UserNotification notification = createNotification();
            notification.markQueued();
            notification.markFailed(FailureReason.SEND_FAILED);
            notification.markFailed(FailureReason.SEND_FAILED);
            notification.markFailed(FailureReason.SEND_FAILED);

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.FAILED);
            assertThat(notification.getRetryInfo().canRetry()).isFalse();
        }

        @Test
        @DisplayName("수동 재시도 시 PENDING으로 복귀, 횟수 초기화")
        void shouldResetOnManualRetry() {
            UserNotification notification = createNotification();
            notification.markQueued();
            notification.markFailed(FailureReason.SEND_FAILED);
            notification.markFailed(FailureReason.SEND_FAILED);
            notification.markFailed(FailureReason.SEND_FAILED);
            notification.retryManually();

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.PENDING);
            assertThat(notification.getRetryInfo().getCount()).isZero();
        }

        @Test
        @DisplayName("PENDING 상태에서 실패 처리 가능 (큐 삽입 실패)")
        void shouldAllowMarkFailedFromPending() {
            UserNotification notification = createNotification();
            notification.markFailed(FailureReason.CHANNEL_UNAVAILABLE);

            assertThat(notification.getRetryInfo().getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("FAILED가 아닌 상태에서 수동 재시도 시 예외")
        void shouldThrowWhenManualRetryFromNonFailed() {
            UserNotification notification = createNotification();

            assertThatThrownBy(notification::retryManually)
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Nested
    @DisplayName("읽음 처리")
    class Read {

        @Test
        @DisplayName("읽음 처리 시 readAt 설정")
        void shouldSetReadAt() {
            UserNotification notification = createNotification();
            notification.markRead();

            assertThat(notification.getReadAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 읽은 알림은 readAt 변경 없음 (멱등)")
        void shouldBeIdempotent() {
            UserNotification notification = createNotification();
            notification.markRead();
            LocalDateTime firstReadAt = notification.getReadAt();

            notification.markRead();

            assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
        }
    }

    @Nested
    @DisplayName("발송 가능 판단")
    class ReadyToSend {

        @Test
        @DisplayName("PENDING이고 scheduledAt 없으면 즉시 발송 가능")
        void shouldBeReadyWhenPendingAndNoSchedule() {
            UserNotification notification = createNotification();

            assertThat(notification.isReadyToSend()).isTrue();
        }

        @Test
        @DisplayName("PENDING이고 scheduledAt 지났으면 발송 가능")
        void shouldBeReadyWhenScheduledTimeHasPassed() {
            UserNotification notification = UserNotification.create(
                    USER_ID, EVENT_ID, TEMPLATE_ID, Channel.IN_APP,
                    new ReferenceData(Map.of()), null, null,
                    LocalDateTime.now().minusMinutes(1)
            );

            assertThat(notification.isReadyToSend()).isTrue();
        }

        @Test
        @DisplayName("PENDING이고 scheduledAt 안 지났으면 발송 불가")
        void shouldNotBeReadyWhenScheduledTimeNotReached() {
            UserNotification notification = UserNotification.create(
                    USER_ID, EVENT_ID, TEMPLATE_ID, Channel.IN_APP,
                    new ReferenceData(Map.of()), null, null,
                    LocalDateTime.now().plusDays(1)
            );

            assertThat(notification.isReadyToSend()).isFalse();
        }

        @Test
        @DisplayName("QUEUED 상태면 발송 불가")
        void shouldNotBeReadyWhenQueued() {
            UserNotification notification = createNotification();
            notification.markQueued();

            assertThat(notification.isReadyToSend()).isFalse();
        }
    }
}
