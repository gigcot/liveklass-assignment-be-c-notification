package com.liveklass.notification.application.port.inbound;

import com.liveklass.notification.domain.model.UserNotification;

import java.util.List;
import java.util.UUID;

public interface RetryNotificationUseCase {

    List<UserNotification> findFailed();

    void retryManually(UUID id);
}
