package pt.uportalegre.estgd.ei.es.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.model.EmailDetails;

import java.util.Optional;

public interface EmailDetailsRepository extends JpaRepository<EmailDetails, Long> {
    //The “Optional” return type is used because the result may be null, so an exception is not thrown.
    Optional<EmailDetails> findByDeliveryId(Long deliveryId);
}