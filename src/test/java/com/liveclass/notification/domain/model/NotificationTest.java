package com.liveclass.notification.domain.model;

import com.liveclass.notification.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class NotificationTest {

    private Notification createNotification() {
        return Notification.create(
                "user-1",
                "evt-1",
                NotificationType.CLASS_REMINDER,
                Channel.IN_APP,
                new ReferenceData(Map.of("courseName", "Spring Boot")),
                null
        );
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("생성 시 PENDING 상태")
        void shouldCreateWithPendingStatus() {
            Notification notification = createNotification();

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.PENDING);
            assertThat(notification.getRetryInfo().getCount()).isZero();
            assertThat(notification.getReadAt()).isNull();
        }

        @Test
        @DisplayName("scheduledAt이 있으면 예약 발송")
        void shouldCreateWithScheduledAt() {
            LocalDateTime scheduled = LocalDateTime.now().plusDays(1);
            Notification notification = Notification.create(
                    "user-1", "evt-1", NotificationType.CLASS_REMINDER,
                    Channel.EMAIL, new ReferenceData(Map.of()), scheduled
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
            Notification notification = createNotification();
            notification.markQueued();

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.QUEUED);
        }

        @Test
        @DisplayName("QUEUED → SENT")
        void shouldTransitionToSent() {
            Notification notification = createNotification();
            notification.markQueued();
            notification.markSent();

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.SENT);
        }

        @Test
        @DisplayName("QUEUED가 아닌 상태에서 SENT 전이 시 예외")
        void shouldThrowWhenMarkSentFromNonQueued() {
            Notification notification = createNotification();

            assertThatThrownBy(notification::markSent)
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 QUEUED 전이 시 예외")
        void shouldThrowWhenMarkQueuedFromNonPending() {
            Notification notification = createNotification();
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
            Notification notification = createNotification();
            notification.markQueued();
            notification.markFailed("timeout");

            assertThat(notification.getSendStatus()).isNotEqualTo(SendStatus.FAILED);
            assertThat(notification.getRetryInfo().canRetry()).isTrue();
        }

        @Test
        @DisplayName("3회 실패 시 FAILED")
        void shouldMarkFailedAfterMaxRetry() {
            Notification notification = createNotification();
            notification.markQueued();
            notification.markFailed("error 1");
            notification.markFailed("error 2");
            notification.markFailed("error 3");

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.FAILED);
            assertThat(notification.getRetryInfo().canRetry()).isFalse();
        }

        @Test
        @DisplayName("수동 재시도 시 PENDING으로 복귀, 횟수 초기화")
        void shouldResetOnManualRetry() {
            Notification notification = createNotification();
            notification.markQueued();
            notification.markFailed("error 1");
            notification.markFailed("error 2");
            notification.markFailed("error 3");
            notification.retryManually();

            assertThat(notification.getSendStatus()).isEqualTo(SendStatus.PENDING);
            assertThat(notification.getRetryInfo().getCount()).isZero();
        }

        @Test
        @DisplayName("PENDING 상태에서 실패 처리 가능 (큐 삽입 실패)")
        void shouldAllowMarkFailedFromPending() {
            Notification notification = createNotification();
            notification.markFailed("queue unavailable");

            assertThat(notification.getRetryInfo().getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("FAILED가 아닌 상태에서 수동 재시도 시 예외")
        void shouldThrowWhenManualRetryFromNonFailed() {
            Notification notification = createNotification();

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
            Notification notification = createNotification();
            notification.markRead();

            assertThat(notification.getReadAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 읽은 알림은 readAt 변경 없음 (멱등)")
        void shouldBeIdempotent() {
            Notification notification = createNotification();
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
            Notification notification = createNotification();

            assertThat(notification.isReadyToSend()).isTrue();
        }

        @Test
        @DisplayName("PENDING이고 scheduledAt 지났으면 발송 가능")
        void shouldBeReadyWhenScheduledTimeHasPassed() {
            Notification notification = Notification.create(
                    "user-1", "evt-1", NotificationType.CLASS_REMINDER,
                    Channel.EMAIL, new ReferenceData(Map.of()),
                    LocalDateTime.now().minusMinutes(1)
            );

            assertThat(notification.isReadyToSend()).isTrue();
        }

        @Test
        @DisplayName("PENDING이고 scheduledAt 안 지났으면 발송 불가")
        void shouldNotBeReadyWhenScheduledTimeNotReached() {
            Notification notification = Notification.create(
                    "user-1", "evt-1", NotificationType.CLASS_REMINDER,
                    Channel.EMAIL, new ReferenceData(Map.of()),
                    LocalDateTime.now().plusDays(1)
            );

            assertThat(notification.isReadyToSend()).isFalse();
        }

        @Test
        @DisplayName("QUEUED 상태면 발송 불가")
        void shouldNotBeReadyWhenQueued() {
            Notification notification = createNotification();
            notification.markQueued();

            assertThat(notification.isReadyToSend()).isFalse();
        }
    }
}
