package pt.uportalegre.estgd.ei.es.notificationservice.worker;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import pt.uportalegre.estgd.ei.es.notificationservice.service.DeliveryProcessor;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "notifications.worker.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundWorker {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundWorker.class);

    private final DeliveryProcessor deliveryProcessor;
    private final ThreadPoolTaskExecutor executor;
    private final int threads;
    private final AtomicInteger activeProcessors = new AtomicInteger();
    private volatile boolean running = true;

    public BackgroundWorker(DeliveryProcessor deliveryProcessor,
                            @Value("${notifications.worker.threads:4}") int threads) {

        if (threads < 1) {
            throw new IllegalArgumentException("notifications.worker.threads must be at least 1.");
        }

        this.deliveryProcessor = deliveryProcessor;
        this.threads = threads;

        this.executor = new ThreadPoolTaskExecutor();
        this.executor.setThreadNamePrefix("notification-worker-");
        this.executor.setCorePoolSize(threads);
        this.executor.setMaxPoolSize(threads);
        this.executor.setQueueCapacity(threads);
        this.executor.setWaitForTasksToCompleteOnShutdown(true);
        this.executor.setAwaitTerminationSeconds(30);
        this.executor.initialize();
    }

    @Scheduled(fixedDelayString = "${notifications.worker.polling-interval-ms:10000}")
    public void processPendingDeliveries() {
        recoverExpiredLocks();
        startIdleProcessors();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        executor.shutdown();
    }

    private void recoverExpiredLocks() {
        try {
            int recovered = deliveryProcessor.recoverExpiredLocks();

            if (recovered > 0) {
                logger.warn("{} interrupted deliveries were released.", recovered);
            }
        } catch (Exception exception) {
            logger.error("Could not recover expired locks: {}", exception.getMessage());
        }
    }

    private void startIdleProcessors() {
        int current = activeProcessors.get();

        while (current < threads) {
            if (activeProcessors.compareAndSet(current, current + 1)) {
                try {
                    executor.execute(this::drainQueue);
                } catch (TaskRejectedException exception) {
                    activeProcessors.decrementAndGet();
                    return;
                }
            }

            current = activeProcessors.get();
        }
    }

    private void drainQueue() {
        String processorId = deliveryProcessor.currentProcessorId();

        try {
            boolean processed = true;

            while (running && processed) {
                processed = deliveryProcessor.processNext(processorId);
            }
        } catch (Exception exception) {
            logger.error("Processor {} stopped: {}", processorId, exception.getMessage());
        } finally {
            activeProcessors.decrementAndGet();
        }
    }
}
