
package pt.uportalegre.estgd.ei.es.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.model.DeliveryAttempt;

import java.util.List;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
    List<DeliveryAttempt> findByDeliveryIdOrderByAttemptNumberAsc(Long deliveryId);
}
