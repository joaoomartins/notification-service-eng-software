package pt.uportalegre.estgd.ei.es.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;

import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    List<NotificationDelivery> findByNotificationId(Long notification);

    @Query("""
            SELECT d
            FROM NotificationDelivery d
            WHERE d.status = :status
              AND d.attemptCount < d.maxAttempts
            ORDER BY d.id ASC
            """)
    List<NotificationDelivery> findEligibleDeliveries(@Param("status") NotificationStatus status);
}