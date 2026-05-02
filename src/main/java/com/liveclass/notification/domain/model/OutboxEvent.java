package com.liveclass.notification.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class OutboxEvent {

    private UUID id;
    private UUID notificationId;
    private OutboxStatus status;
    private OutboxPayload payload;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;

    public static OutboxEvent create(UUID notificationId, OutboxPayload payload, LocalDateTime scheduledAt) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.notificationId = notificationId;
        event.status = OutboxStatus.PENDING;
        event.payload = payload;
        event.scheduledAt = scheduledAt;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    public static OutboxEvent reconstruct(
            UUID id, UUID notificationId, OutboxStatus status,
            OutboxPayload payload, LocalDateTime scheduledAt, LocalDateTime createdAt
    ) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.notificationId = notificationId;
        event.status = status;
        event.payload = payload;
        event.scheduledAt = scheduledAt;
        event.createdAt = createdAt;
        return event;
    }

    public void markRelayed() {
        this.status = OutboxStatus.RELAYED;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public OutboxStatus getStatus() { return status; }
    public OutboxPayload getPayload() { return payload; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
