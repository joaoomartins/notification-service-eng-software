package pt.uportalegre.estgd.ei.es.notificationservice.dto;

import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.List;

public class DeliveryDetailsDto {
    private final Long id;
    private final NotificationType type;
    private final NotificationStatus status;
    private final int attemptCount;
    private final int maxAttempts;
    private final LocalDateTime sentAt;
    private final String lastErrorMessage;
    private final List<DeliveryAttemptDto> attempts;

    public DeliveryDetailsDto(
            Long id,
            NotificationType type,
            NotificationStatus status,
            int attemptCount,
            int maxAttempts,
            LocalDateTime sentAt,
            String lastErrorMessage,
            List<DeliveryAttemptDto> attempts) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.sentAt = sentAt;
        this.lastErrorMessage = lastErrorMessage;
        this.attempts = List.copyOf(attempts);
    }

    public Long getId() {
        return id;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public List<DeliveryAttemptDto> getAttempts() {
        return attempts;
    }
}
