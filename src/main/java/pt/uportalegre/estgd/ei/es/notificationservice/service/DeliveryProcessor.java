package pt.uportalegre.estgd.ei.es.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pt.uportalegre.estgd.ei.es.notificationservice.model.DeliveryAttempt;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.DeliveryAttemptResult;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.DeliveryAttemptRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.sender.NotificationSender;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class DeliveryProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryProcessor.class);

    private static final int MAX_CLAIM_ROUNDS = 10;

    private static final List<NotificationPriority> PRIORITIES_BY_WEIGHT = Arrays.stream(NotificationPriority.values())
            .sorted(Comparator.comparingInt(NotificationPriority::getWeight))
            .toList();

    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final List<NotificationSender> senders;
    private final TransactionTemplate transactionTemplate;
    private final int claimWindow;
    private final Duration leaseTimeout;
    private final Duration agingThreshold;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    public DeliveryProcessor(NotificationDeliveryRepository deliveryRepository,
                             DeliveryAttemptRepository attemptRepository,
                             List<NotificationSender> senders,
                             PlatformTransactionManager transactionManager,
                             @Value("${notifications.worker.claim-window:10}") int claimWindow,
                             @Value("${notifications.worker.lease-timeout-seconds:60}") long leaseTimeoutSeconds,
                             @Value("${notifications.worker.aging-threshold-seconds:300}") long agingThresholdSeconds) {

        if (claimWindow < 1) {
            throw new IllegalArgumentException("notifications.worker.claim-window must be at least 1.");
        }

        if (leaseTimeoutSeconds < 1) {
            throw new IllegalArgumentException("notifications.worker.lease-timeout-seconds must be at least 1.");
        }

        if (agingThresholdSeconds < 1) {
            throw new IllegalArgumentException("notifications.worker.aging-threshold-seconds must be at least 1.");
        }

        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.senders = senders;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.claimWindow = claimWindow;
        this.leaseTimeout = Duration.ofSeconds(leaseTimeoutSeconds);
        this.agingThreshold = Duration.ofSeconds(agingThresholdSeconds);
    }

    public String currentProcessorId() {
        return instanceId + "/" + Thread.currentThread().getName();
    }

    public boolean claim(Long deliveryId, String processorId) {
        return deliveryRepository.claim(deliveryId, NotificationStatus.PENDING, NotificationStatus.PROCESSING,
                processorId, LocalDateTime.now()) == 1;
    }

    public Optional<Long> claimNext(String processorId) {
        Pageable window = PageRequest.of(0, claimWindow);
        LocalDateTime agingCutoff = LocalDateTime.now().minus(agingThreshold);

        Optional<Long> aged = claimFirst(processorId,
                () -> deliveryRepository.findAgedIds(NotificationStatus.PENDING, agingCutoff, window));

        if (aged.isPresent()) {
            return aged;
        }

        for (NotificationPriority priority : PRIORITIES_BY_WEIGHT) {
            Optional<Long> claimed = claimFirst(processorId,
                    () -> deliveryRepository.findNextIdsByPriority(NotificationStatus.PENDING, priority, window));

            if (claimed.isPresent()) {
                return claimed;
            }
        }

        return Optional.empty();
    }

    public boolean processNext(String processorId) {
        Optional<Long> claimed = claimNext(processorId);

        if (claimed.isEmpty()) {
            return false;
        }

        process(claimed.get(), processorId);

        return true;
    }

    public boolean process(Long deliveryId, String processorId) {
        NotificationDelivery claimed = findDelivery(deliveryId);

        if (claimed.getStatus() != NotificationStatus.PROCESSING || !processorId.equals(claimed.getLockedBy())) {
            return false;
        }

        int attemptNumber = claimed.getAttemptCount();
        Exception failure = trySend(deliveryId);

        if (failure == null) {
            return recordResult(deliveryId, processorId, attemptNumber, NotificationStatus.SENT,
                    LocalDateTime.now(), DeliveryAttemptResult.SUCCESS, null);
        }

        NotificationStatus failureStatus = attemptNumber >= claimed.getMaxAttempts()
                ? NotificationStatus.FAILED
                : NotificationStatus.PENDING;

        return recordResult(deliveryId, processorId, attemptNumber, failureStatus,
                null, DeliveryAttemptResult.FAILURE, failure.getMessage());
    }

    public int recoverExpiredLocks() {
        LocalDateTime threshold = LocalDateTime.now().minus(leaseTimeout);
        int recovered = 0;

        for (NotificationDelivery expired : deliveryRepository.findByStatusAndLockedAtBefore(NotificationStatus.PROCESSING, threshold)) {
            if (release(expired)) {
                recovered++;
            }
        }

        return recovered;
    }

    private Optional<Long> claimFirst(String processorId, Supplier<List<Long>> candidatesSource) {
        for (int round = 0; round < MAX_CLAIM_ROUNDS; round++) {
            List<Long> candidates = candidatesSource.get();

            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            for (Long candidate : candidates) {
                if (claim(candidate, processorId)) {
                    return Optional.of(candidate);
                }
            }
        }

        return Optional.empty();
    }

    private Exception trySend(Long deliveryId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                NotificationDelivery delivery = findDelivery(deliveryId);
                findSender(delivery).send(delivery);
            });

            return null;
        } catch (Exception exception) {
            return exception;
        }
    }

    private boolean recordResult(Long deliveryId, String processorId, int attemptNumber, NotificationStatus newStatus,
                                 LocalDateTime sentAt, DeliveryAttemptResult result, String errorMessage) {

        Boolean recorded = transactionTemplate.execute(status -> {
            int updated = deliveryRepository.finish(deliveryId, processorId, NotificationStatus.PROCESSING, newStatus, sentAt);

            if (updated == 0) {
                return false;
            }

            attemptRepository.save(new DeliveryAttempt(deliveryRepository.getReferenceById(deliveryId),
                    attemptNumber, result, errorMessage));

            return true;
        });

        if (!Boolean.TRUE.equals(recorded)) {
            logger.warn("Delivery {} is no longer locked by {}: the result of attempt {} was discarded.",
                    deliveryId, processorId, attemptNumber);
        }

        return Boolean.TRUE.equals(recorded);
    }

    private boolean release(NotificationDelivery expired) {
        Long deliveryId = expired.getId();
        String holder = expired.getLockedBy();
        int attemptNumber = expired.getAttemptCount();

        NotificationStatus newStatus = attemptNumber >= expired.getMaxAttempts()
                ? NotificationStatus.FAILED
                : NotificationStatus.PENDING;

        String message = "Processing interrupted: the lock held by " + holder + " expired.";

        boolean released = recordResult(deliveryId, holder, attemptNumber, newStatus,
                null, DeliveryAttemptResult.FAILURE, message);

        if (released) {
            logger.warn("Delivery {} was released after the lock held by {} expired.", deliveryId, holder);
        }

        return released;
    }

    private NotificationDelivery findDelivery(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));
    }

    private NotificationSender findSender(NotificationDelivery delivery) {
        for (NotificationSender sender : senders) {
            if (sender.supports(delivery.getType())) {
                return sender;
            }
        }

        throw new IllegalStateException("No sender available for type: " + delivery.getType());
    }
}
