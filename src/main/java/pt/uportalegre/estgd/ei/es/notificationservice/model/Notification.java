package pt.uportalegre.estgd.ei.es.notificationservice.model;

import jakarta.persistence.*;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist //executed before save
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    protected Notification() {

    }

    public Notification(String content, NotificationPriority priority) {
        this.content = content;
        this.priority = priority;
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}