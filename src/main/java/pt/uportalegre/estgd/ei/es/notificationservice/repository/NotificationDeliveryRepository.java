package pt.uportalegre.estgd.ei.es.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;

import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    List<NotificationDelivery> findByNotificationId(Long notification);
}
