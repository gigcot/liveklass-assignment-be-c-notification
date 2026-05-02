package com.liveklass.notification.adapter.outbound.persistence;

import com.liveklass.notification.domain.model.*;
import com.liveklass.notification.domain.model.Channel;
import com.liveklass.notification.domain.model.OutboxEvent;
import com.liveklass.notification.domain.model.OutboxPayload;
import com.liveklass.notification.domain.model.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OutboxRelayDeduplicationTest {

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Test
    @DisplayName("PENDING 이벤트만 폴링 대상에 포함된다")
    void findPendingForRelay_excludes_relayed_events() {
        OutboxJpaEntity pending = buildEntity(OutboxStatus.PENDING, LocalDateTime.now().minusSeconds(1));
        OutboxJpaEntity relayed = buildEntity(OutboxStatus.RELAYED, LocalDateTime.now().minusSeconds(1));
        outboxJpaRepository.saveAll(List.of(pending, relayed));

        List<OutboxJpaEntity> result = outboxJpaRepository.findPendingForRelay(LocalDateTime.now(), 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(pending.getId());
    }

    @Test
    @DisplayName("scheduledAt이 현재 시각 이후면 폴링 대상에서 제외된다")
    void findPendingForRelay_excludes_future_scheduled() {
        OutboxJpaEntity future = buildEntity(OutboxStatus.PENDING, LocalDateTime.now().plusHours(1));
        outboxJpaRepository.save(future);

        List<OutboxJpaEntity> result = outboxJpaRepository.findPendingForRelay(LocalDateTime.now(), 100);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("markRelayed 후 동일 이벤트는 재폴링되지 않는다")
    void markRelayed_prevents_repolling() {
        OutboxJpaEntity entity = buildEntity(OutboxStatus.PENDING, LocalDateTime.now().minusSeconds(1));
        outboxJpaRepository.save(entity);

        outboxJpaRepository.markRelayed(entity.getId());

        List<OutboxJpaEntity> result = outboxJpaRepository.findPendingForRelay(LocalDateTime.now(), 100);
        assertThat(result).isEmpty();
    }

    private OutboxJpaEntity buildEntity(OutboxStatus status, LocalDateTime scheduledAt) {
        UUID notificationId = UUID.randomUUID();
        OutboxPayload payload = new OutboxPayload(
                notificationId, UUID.randomUUID(), Channel.IN_APP, "테스트"
        );
        OutboxEvent domain = OutboxEvent.create(notificationId, payload, scheduledAt);
        if (status == OutboxStatus.RELAYED) {
            domain.markRelayed();
        }
        return OutboxJpaEntity.fromDomain(domain);
    }
}
