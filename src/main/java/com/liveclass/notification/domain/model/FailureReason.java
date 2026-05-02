package com.liveclass.notification.domain.model;

public enum FailureReason {

    SEND_FAILED,
    TIMEOUT,
    CHANNEL_UNAVAILABLE;

    public String toMessage() {
        return name().toLowerCase().replace('_', ' ');
    }
}
