package com.liveklass.notification.adapter.outbound.persistence;

import com.liveklass.notification.domain.model.*;

import com.liveklass.notification.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationJpaRepositoryTest {

    @Autowired
    private NotificationJpaRepository repository;

    @Test
    void 저장_후_ID로_조회() {
        NotificationJpaEntity entity = buildEntity(UUID.randomUUID(), UUID.randomUUID(), null);
        repository.save(entity);

        assertThat(repository.findById(entity.getId())).isPresent();
    }

    @Test
    void userId로_목록_조회() {
        UUID userId = UUID.randomUUID();
        repository.save(buildEntity(userId, UUID.randomUUID(), null));
        repository.save(buildEntity(userId, UUID.randomUUID(), null));
        repository.save(buildEntity(UUID.randomUUID(), UUID.randomUUID(), null)); // 다른 유저

        List<NotificationJpaEntity> result = repository.findByUserId(userId);

        assertThat(result).hasSize(2);
    }

    @Test
    void 안읽음_필터() {
        UUID userId = UUID.randomUUID();
        NotificationJpaEntity unread = buildEntity(userId, UUID.randomUUID(), null);
        NotificationJpaEntity read = buildEntity(userId, UUID.randomUUID(), LocalDateTime.now());
        repository.save(unread);
        repository.save(read);

        assertThat(repository.findByUserIdAndReadAtIsNull(userId)).hasSize(1);
        assertThat(repository.findByUserIdAndReadAtIsNotNull(userId)).hasSize(1);
    }

    @Test
    void 중복_체크() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        repository.save(buildEntity(userId, eventId, null));

        assertThat(repository.existsByEventIdAndUserId(eventId, userId)).isTrue();
        assertThat(repository.existsByEventIdAndUserId(UUID.randomUUID(), userId)).isFalse();
    }

    private NotificationJpaEntity buildEntity(UUID userId, UUID eventId, LocalDateTime readAt) {
        UserNotification domain = UserNotification.reconstruct(
                UUID.randomUUID(), userId, eventId, UUID.randomUUID(), Channel.IN_APP,
                SendStatus.PENDING, new ReferenceData(Map.of()),
                new RetryInfo(), null, null,
                null, null, readAt,
                LocalDateTime.now(), LocalDateTime.now()
        );
        return NotificationJpaEntity.fromDomain(domain);
    }
}
