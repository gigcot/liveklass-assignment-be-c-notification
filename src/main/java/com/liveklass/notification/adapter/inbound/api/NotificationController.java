package com.liveklass.notification.adapter.inbound.api;

import com.liveklass.notification.adapter.inbound.api.dto.NotificationResponse;
import com.liveklass.notification.adapter.inbound.api.dto.RegisterNotificationRequest;
import com.liveklass.notification.domain.model.UserNotification;
import com.liveklass.notification.application.port.inbound.GetNotificationUseCase;
import com.liveklass.notification.application.port.inbound.MarkReadUseCase;
import com.liveklass.notification.application.port.inbound.RegisterNotificationUseCase;
import com.liveklass.notification.application.port.inbound.RetryNotificationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final RegisterNotificationUseCase registerNotificationUseCase;
    private final GetNotificationUseCase getNotificationUseCase;
    private final MarkReadUseCase markReadUseCase;
    private final RetryNotificationUseCase retryNotificationUseCase;

    public NotificationController(RegisterNotificationUseCase registerNotificationUseCase,
                                  GetNotificationUseCase getNotificationUseCase,
                                  MarkReadUseCase markReadUseCase,
                                  RetryNotificationUseCase retryNotificationUseCase) {
        this.registerNotificationUseCase = registerNotificationUseCase;
        this.getNotificationUseCase = getNotificationUseCase;
        this.markReadUseCase = markReadUseCase;
        this.retryNotificationUseCase = retryNotificationUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> send(@Valid @RequestBody RegisterNotificationRequest request) {
        UUID id = registerNotificationUseCase.register(
                request.userId(), request.eventId(), request.templateId(),
                request.notificationType(), request.channel(),
                request.referenceData(), request.scheduledAt()
        );
        return ResponseEntity.created(URI.create("/api/notifications/" + id)).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getById(@PathVariable UUID id) {
        UserNotification notification = getNotificationUseCase.getById(id);
        return ResponseEntity.ok(NotificationResponse.from(notification));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        markReadUseCase.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/failed")
    public ResponseEntity<List<NotificationResponse>> getFailed() {
        List<NotificationResponse> responses = retryNotificationUseCase.findFailed().stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable UUID id) {
        retryNotificationUseCase.retryManually(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<List<NotificationResponse>> getByUserId(
            @PathVariable UUID userId,
            @RequestParam(required = false) Boolean unreadOnly) {
        List<NotificationResponse> responses = getNotificationUseCase.getByUserId(userId, unreadOnly)
                .stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
