package pt.uportalegre.estgd.ei.es.notificationservice.model;

import jakarta.persistence.*;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.DeliveryAttemptResult;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private NotificationDelivery delivery;

    @Column(nullable = false)
    private int attemptNumber;

    @Column (nullable = false)
    private LocalDateTime attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryAttemptResult result;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    protected DeliveryAttempt() {
    }

    public DeliveryAttempt(NotificationDelivery delivery, int attemptNumber, DeliveryAttemptResult result, String errorMessage) {
        this.delivery = delivery;
        this.attemptNumber = attemptNumber;
        this.attemptedAt = LocalDateTime.now();
        this.result = result;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public NotificationDelivery getDelivery() {
        return delivery;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public DeliveryAttemptResult getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
