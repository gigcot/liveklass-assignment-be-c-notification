package com.liveclass.notification.domain.exception;

public class OutboxSerializationException extends RuntimeException {

    private OutboxSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static OutboxSerializationException onSerialize(Throwable cause) {
        return new OutboxSerializationException("OutboxPayload 직렬화 실패", cause);
    }

    public static OutboxSerializationException onDeserialize(Throwable cause) {
        return new OutboxSerializationException("OutboxPayload 역직렬화 실패", cause);
    }
}
