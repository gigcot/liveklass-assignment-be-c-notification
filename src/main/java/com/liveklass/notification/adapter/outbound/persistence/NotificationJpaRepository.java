package com.liveklass.notification.adapter.outbound.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.liveklass.notification.domain.model.SendStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM NotificationJpaEntity n WHERE n.id = :id")
    Optional<NotificationJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<NotificationJpaEntity> findByUserId(UUID userId);

    List<NotificationJpaEntity> findByUserIdAndReadAtIsNull(UUID userId);

    List<NotificationJpaEntity> findByUserIdAndReadAtIsNotNull(UUID userId);

    List<NotificationJpaEntity> findBySendStatus(SendStatus sendStatus);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);
}
