package com.liveclass.notification.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationTemplate {

    private UUID id;
    private NotificationType type;
    private Channel channel;
    private String title;
    private String body;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NotificationTemplate create(
            NotificationType type,
            Channel channel,
            String title,
            String body
    ) {
        NotificationTemplate template = new NotificationTemplate();
        template.id = UUID.randomUUID();
        template.type = type;
        template.channel = channel;
        template.title = title;
        template.body = body;
        template.createdAt = LocalDateTime.now();
        template.updatedAt = LocalDateTime.now();
        return template;
    }

    public String renderTitle(ReferenceData data) {
        return replacePlaceholders(this.title, data);
    }

    public String renderBody(ReferenceData data) {
        return replacePlaceholders(this.body, data);
    }

    private String replacePlaceholders(String template, ReferenceData data) {
        String result = template;
        for (var entry : data.toMap().entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public static NotificationTemplate reconstruct(
            UUID id, NotificationType type, Channel channel,
            String title, String body,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        NotificationTemplate template = new NotificationTemplate();
        template.id = id;
        template.type = type;
        template.channel = channel;
        template.title = title;
        template.body = body;
        template.createdAt = createdAt;
        template.updatedAt = updatedAt;
        return template;
    }

    public void update(String title, String body) {
        this.title = title;
        this.body = body;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters
    public UUID getId() { return id; }
    public NotificationType getType() { return type; }
    public Channel getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
