package pt.uportalegre.estgd.ei.es.notificationservice.dto;

import java.time.LocalDateTime;

public class NotificationListItem {
    private final Long id;
    private final String subject;
    private final LocalDateTime createdAt;

    public NotificationListItem(
            Long id,
            String subject,
            LocalDateTime createdAt) {
        this.id = id;
        this.subject = subject;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}