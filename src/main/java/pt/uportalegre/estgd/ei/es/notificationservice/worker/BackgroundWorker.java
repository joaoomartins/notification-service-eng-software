package pt.uportalegre.estgd.ei.es.notificationservice.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.service.NotificationService;

import java.util.List;

/**
 * Periodically picks up pending deliveries and asks the service to process them.
 * The worker only decides WHEN to process; the service decides WHAT happens to each delivery.
 */
@Component
@ConditionalOnProperty(name = "notifications.worker.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundWorker {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundWorker.class);

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationService notificationService;

    public BackgroundWorker(NotificationDeliveryRepository deliveryRepository, NotificationService notificationService) {
        this.deliveryRepository = deliveryRepository;
        this.notificationService = notificationService;
    }

    // fixedDelay: the next cycle only starts after the previous one finishes, so cycles never overlap.
    @Scheduled(fixedDelayString = "${notifications.worker.polling-interval-ms:10000}")
    public void processPendingDeliveries() {
        List<NotificationDelivery> deliveries = deliveryRepository.findEligibleDeliveries(NotificationStatus.PENDING);

        for (NotificationDelivery delivery : deliveries) {
            try {
                logger.info("Processing delivery {}. Attempt {}/{}.",
                        delivery.getId(), delivery.getAttemptCount() + 1, delivery.getMaxAttempts());

                notificationService.sendDelivery(delivery.getId());

            } catch (Exception exception) {
                logger.error("Error processing delivery {}.", delivery.getId(), exception);
            }
        }
    }
}
