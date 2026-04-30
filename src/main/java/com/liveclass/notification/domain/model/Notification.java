package com.liveclass.notification.domain.model;

import com.liveclass.notification.domain.exception.InvalidStatusTransitionException;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification {

    private UUID id;
    private String userId;
    private String eventId;
    private NotificationType type;
    private Channel channel;
    private SendStatus sendStatus;
    private ReferenceData referenceData;
    private RetryInfo retryInfo;

    private LocalDateTime scheduledAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Notification create(
            String userId,
            String eventId,
            NotificationType type,
            Channel channel,
            ReferenceData referenceData,
            LocalDateTime scheduledAt
    ) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.userId = userId;
        notification.eventId = eventId;
        notification.type = type;
        notification.channel = channel;
        notification.sendStatus = SendStatus.PENDING;
        notification.referenceData = referenceData;
        notification.retryInfo = new RetryInfo();
        notification.scheduledAt = scheduledAt;
        notification.createdAt = LocalDateTime.now();
        notification.updatedAt = LocalDateTime.now();
        return notification;
    }

    public void markQueued() {
        if (this.sendStatus != SendStatus.PENDING) {
            throw new InvalidStatusTransitionException(this.sendStatus, SendStatus.QUEUED);
        }
        this.sendStatus = SendStatus.QUEUED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markSent() {
        if (this.sendStatus != SendStatus.QUEUED) {
            throw new InvalidStatusTransitionException(this.sendStatus, SendStatus.SENT);
        }
        this.sendStatus = SendStatus.SENT;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        if (this.sendStatus != SendStatus.QUEUED && this.sendStatus != SendStatus.PENDING) {
            throw new InvalidStatusTransitionException(this.sendStatus, SendStatus.FAILED);
        }
        this.retryInfo = retryInfo.recordFailure(reason);
        if (!retryInfo.canRetry()) {
            this.sendStatus = SendStatus.FAILED;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void markRead() {
        if (this.readAt != null) {
            return; // 이미 읽음 — 멱등성
        }
        this.readAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void retryManually() {
        if (this.sendStatus != SendStatus.FAILED) {
            throw new InvalidStatusTransitionException(this.sendStatus, SendStatus.PENDING);
        }
        this.retryInfo = retryInfo.reset();
        this.sendStatus = SendStatus.PENDING;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isReadyToSend() {
        return this.sendStatus == SendStatus.PENDING
                && (this.scheduledAt == null || !this.scheduledAt.isAfter(LocalDateTime.now()));
    }

    // Getters
    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getEventId() { return eventId; }
    public NotificationType getType() { return type; }
    public Channel getChannel() { return channel; }
    public SendStatus getSendStatus() { return sendStatus; }
    public ReferenceData getReferenceData() { return referenceData; }
    public RetryInfo getRetryInfo() { return retryInfo; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
