package com.liveklass.notification.adapter.outbound.persistence;

import com.liveklass.notification.application.port.outbound.NotificationRepository;
import com.liveklass.notification.domain.model.SendStatus;
import com.liveklass.notification.domain.model.UserNotification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public UserNotification save(UserNotification notification) {
        NotificationJpaEntity entity = NotificationJpaEntity.fromDomain(notification);
        jpaRepository.save(entity);
        return notification;
    }

    @Override
    public Optional<UserNotification> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(NotificationJpaEntity::toDomain);
    }

    @Override
    public Optional<UserNotification> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id)
                .map(NotificationJpaEntity::toDomain);
    }

    @Override
    public List<UserNotification> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<UserNotification> findByUserIdAndReadAtIsNull(UUID userId) {
        return jpaRepository.findByUserIdAndReadAtIsNull(userId).stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<UserNotification> findByUserIdAndReadAtIsNotNull(UUID userId) {
        return jpaRepository.findByUserIdAndReadAtIsNotNull(userId).stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<UserNotification> findBySendStatus(SendStatus status) {
        return jpaRepository.findBySendStatus(status).stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsByEventIdAndUserId(UUID eventId, UUID userId) {
        return jpaRepository.existsByEventIdAndUserId(eventId, userId);
    }
}
