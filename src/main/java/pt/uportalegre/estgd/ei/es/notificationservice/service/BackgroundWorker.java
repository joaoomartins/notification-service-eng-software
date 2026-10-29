
package pt.uportalegre.estgd.ei.es.notificationservice.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class BackgroundWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundWorker.class);

    private static final long POLLING_INTERVAL_MS = 10000;

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationService notificationService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread workerThread;

    public BackgroundWorker(NotificationDeliveryRepository deliveryRepository, NotificationService notificationService) {
        this.deliveryRepository = deliveryRepository;
        this.notificationService = notificationService;
    }

    @PostConstruct
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        workerThread = new Thread(this, "NotificationBackgroundWorker");
        workerThread.setDaemon(false);
        workerThread.start();

        logger.info("BackgroundWorker iniciado.");
    }

    @PreDestroy
    public void stop() {
        running.set(false);

        if (workerThread != null) {
            workerThread.interrupt();

            try {
                workerThread.join(2000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        logger.info("BackgroundWorker parado.");
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                processPendingDeliveries();
                Thread.sleep(POLLING_INTERVAL_MS);

            } catch (InterruptedException exception) {
                break;

            } catch (Exception exception) {
                logger.error("Erro durante o ciclo do BackgroundWorker.", exception);

                try {
                    Thread.sleep(POLLING_INTERVAL_MS);
                } catch (InterruptedException interruptedException) {
                    break;
                }
            }
        }
    }

    private void processPendingDeliveries() {
        List<NotificationDelivery> deliveries = deliveryRepository.findEligibleDeliveries(NotificationStatus.PENDING);

        for (NotificationDelivery delivery : deliveries) {
            if (!running.get()) {
                break;
            }

            try {
                logger.info("A processar entrega {}. Tentativa {}/{}.", delivery.getId(), delivery.getAttemptCount() + 1, delivery.getMaxAttempts());

                notificationService.sendDelivery(delivery.getId());

            } catch (Exception exception) {
                logger.error("Erro ao processar a entrega {}.", delivery.getId(), exception);
            }
        }
    }
}
