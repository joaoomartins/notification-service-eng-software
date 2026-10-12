package pt.uportalegre.estgd.ei.es.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
