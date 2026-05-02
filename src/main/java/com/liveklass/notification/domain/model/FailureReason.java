package com.liveklass.notification.domain.model;

public enum FailureReason {

    SEND_FAILED,
    TIMEOUT,
    CHANNEL_UNAVAILABLE,
    STALE_TIMEOUT;

    public String toMessage() {
        return name().toLowerCase().replace('_', ' ');
    }
}
