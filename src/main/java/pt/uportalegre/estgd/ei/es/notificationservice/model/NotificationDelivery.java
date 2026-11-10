
package pt.uportalegre.estgd.ei.es.notificationservice.model;

import jakarta.persistence.*;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private int maxAttempts;

    @Column(nullable = false)
    private int attemptCount;

    private LocalDateTime sentAt;

    protected NotificationDelivery() {
    }

    public NotificationDelivery(Notification notification, NotificationType type) {
        this(notification, type, 3);
    }

    public NotificationDelivery(Notification notification, NotificationType type, int maxAttempts) {

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Max attempts must be at least 1.");
        }

        this.notification = notification;
        this.type = type;
        this.status = NotificationStatus.PENDING;
        this.maxAttempts = maxAttempts;
        this.attemptCount = 0;
    }

    public Long getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void incrementAttemptCount() {
        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException("Max number of attempts has been reached.");
        }

        attemptCount++;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
