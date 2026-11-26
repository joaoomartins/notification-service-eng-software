package pt.uportalegre.estgd.ei.es.notificationservice.dto;

import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;

import java.time.LocalDateTime;

public class MonitorItemDto {
    private final Long deliveryId;
    private final Long notificationId;
    private final NotificationPriority priority;
    private final String processor;
    private final LocalDateTime lockedAt;
    private final LocalDateTime sentAt;
    private final int attemptCount;
    private final int maxAttempts;

    public MonitorItemDto(
            Long deliveryId,
            Long notificationId,
            NotificationPriority priority,
            String processor,
            LocalDateTime lockedAt,
            LocalDateTime sentAt,
            int attemptCount,
            int maxAttempts) {
        this.deliveryId = deliveryId;
        this.notificationId = notificationId;
        this.priority = priority;
        this.processor = processor;
        this.lockedAt = lockedAt;
        this.sentAt = sentAt;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public String getProcessor() {
        return processor;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
