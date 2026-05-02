package com.liveclass.notification.domain.exception;

import com.liveclass.notification.domain.model.Channel;
import com.liveclass.notification.domain.model.NotificationType;

import java.util.UUID;

public class TemplateNotFoundException extends RuntimeException {

    private TemplateNotFoundException(String message) {
        super(message);
    }

    public static TemplateNotFoundException byId(UUID id) {
        return new TemplateNotFoundException("템플릿을 찾을 수 없습니다. id=" + id);
    }

    public static TemplateNotFoundException byTypeAndChannel(NotificationType type, Channel channel) {
        return new TemplateNotFoundException("템플릿을 찾을 수 없습니다. type=" + type + ", channel=" + channel);
    }
}
