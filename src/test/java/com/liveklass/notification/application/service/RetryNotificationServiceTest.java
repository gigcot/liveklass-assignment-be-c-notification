package com.liveklass.notification.application.service;

import com.liveklass.notification.application.port.outbound.NotificationRegistrationPort;
import com.liveklass.notification.application.port.outbound.NotificationRepository;
import com.liveklass.notification.domain.exception.NotificationNotFoundException;
import com.liveklass.notification.domain.exception.InvalidStatusTransitionException;
import com.liveklass.notification.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class RetryNotificationServiceTest {

    private RetryNotificationService service;
    private FakeNotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository = new FakeNotificationRepository();
        service = new RetryNotificationService(notificationRepository, (n, o) -> n.getId());
    }

    @Test
    void findFailed_FAILED_상태만_반환() {
        UserNotification failed = failedNotification();
        UserNotification pending = pendingNotification();
        notificationRepository.store.put(failed.getId(), failed);
        notificationRepository.store.put(pending.getId(), pending);

        assertThat(service.findFailed()).containsExactly(failed);
    }

    @Test
    void retryManually_PENDING으로_초기화() {
        UserNotification failed = failedNotification();
        notificationRepository.store.put(failed.getId(), failed);

        service.retryManually(failed.getId());

        assertThat(notificationRepository.store.get(failed.getId()).getSendStatus())
                .isEqualTo(SendStatus.PENDING);
    }

    @Test
    void retryManually_없는_알림이면_예외() {
        assertThatThrownBy(() -> service.retryManually(UUID.randomUUID()))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void retryManually_FAILED_아닌_상태면_예외() {
        UserNotification pending = pendingNotification();
        notificationRepository.store.put(pending.getId(), pending);

        assertThatThrownBy(() -> service.retryManually(pending.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ── helpers ──────────────────────────────────────────────

    private UserNotification failedNotification() {
        return UserNotification.reconstruct(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Channel.IN_APP, SendStatus.FAILED, new ReferenceData(Map.of()),
                new RetryInfo(), "제목", "본문", null, null, null,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
    }

    private UserNotification pendingNotification() {
        return UserNotification.reconstruct(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Channel.IN_APP, SendStatus.PENDING, new ReferenceData(Map.of()),
                new RetryInfo(), "제목", "본문", null, null, null,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
    }

    // ── Fake ──────────────────────────────────────────────

    static class FakeNotificationRepository implements NotificationRepository {
        final Map<UUID, UserNotification> store = new HashMap<>();

        @Override
        public UserNotification save(UserNotification n) { store.put(n.getId(), n); return n; }

        @Override
        public Optional<UserNotification> findById(UUID id) { return Optional.ofNullable(store.get(id)); }

        @Override
        public Optional<UserNotification> findByIdForUpdate(UUID id) { return Optional.ofNullable(store.get(id)); }

        @Override
        public List<UserNotification> findByUserId(UUID userId) {
            return store.values().stream().filter(n -> n.getUserId().equals(userId)).toList();
        }

        @Override
        public List<UserNotification> findByUserIdAndReadAtIsNull(UUID userId) {
            return store.values().stream()
                    .filter(n -> n.getUserId().equals(userId) && n.getReadAt() == null).toList();
        }

        @Override
        public List<UserNotification> findByUserIdAndReadAtIsNotNull(UUID userId) {
            return store.values().stream()
                    .filter(n -> n.getUserId().equals(userId) && n.getReadAt() != null).toList();
        }

        @Override
        public List<UserNotification> findBySendStatus(SendStatus status) {
            return store.values().stream().filter(n -> n.getSendStatus() == status).toList();
        }

        @Override
        public boolean existsByEventIdAndUserId(UUID eventId, UUID userId) {
            return store.values().stream()
                    .anyMatch(n -> n.getEventId().equals(eventId) && n.getUserId().equals(userId));
        }
    }
}
