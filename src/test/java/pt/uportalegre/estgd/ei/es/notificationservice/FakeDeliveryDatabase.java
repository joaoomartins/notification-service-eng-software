package pt.uportalegre.estgd.ei.es.notificationservice;

import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import pt.uportalegre.estgd.ei.es.notificationservice.model.DeliveryAttempt;
import pt.uportalegre.estgd.ei.es.notificationservice.model.Notification;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.DeliveryAttemptRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

class FakeDeliveryDatabase {

    private static final Comparator<NotificationDelivery> QUEUE_ORDER = Comparator
            .comparing((NotificationDelivery delivery) -> delivery.getNotification().getCreatedAt())
            .thenComparing(NotificationDelivery::getId);

    private final Map<Long, NotificationDelivery> deliveries = new LinkedHashMap<>();
    private final List<DeliveryAttempt> attempts = new ArrayList<>();
    private final LocalDateTime start = LocalDateTime.now().minusSeconds(60);
    private long nextId = 1;

    FakeDeliveryDatabase(NotificationDeliveryRepository deliveryRepository, DeliveryAttemptRepository attemptRepository) {
        lenient().when(deliveryRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(get(invocation.getArgument(0))));

        lenient().when(deliveryRepository.getReferenceById(any()))
                .thenAnswer(invocation -> get(invocation.getArgument(0)));

        lenient().when(deliveryRepository.claim(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> claim(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4)));

        lenient().when(deliveryRepository.finish(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> finish(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4)));

        lenient().when(deliveryRepository.findNextIdsByPriority(any(), any(), any()))
                .thenAnswer(invocation -> findNextIds(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2)));

        lenient().when(deliveryRepository.findAgedIds(any(), any(), any()))
                .thenAnswer(invocation -> findAgedIds(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2)));

        lenient().when(deliveryRepository.findByStatusAndLockedAtBefore(any(), any()))
                .thenAnswer(invocation -> findExpired(invocation.getArgument(0), invocation.getArgument(1)));

        lenient().when(deliveryRepository.countByStatus(any()))
                .thenAnswer(invocation -> count(invocation.getArgument(0)));

        lenient().when(deliveryRepository.countByStatusAndNotificationPriority(any(), any()))
                .thenAnswer(invocation -> count(invocation.getArgument(0), invocation.getArgument(1)));

        lenient().when(deliveryRepository.findByStatusOrderByLockedAtAsc(any()))
                .thenAnswer(invocation -> findProcessing(invocation.getArgument(0)));

        lenient().when(deliveryRepository.findTop10ByStatusOrderBySentAtDesc(any()))
                .thenAnswer(invocation -> findRecentlySent(invocation.getArgument(0)));

        lenient().when(attemptRepository.save(any(DeliveryAttempt.class)))
                .thenAnswer(invocation -> record(invocation.getArgument(0)));

        lenient().when(attemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(any()))
                .thenAnswer(invocation -> findAttempts(invocation.getArgument(0)));
    }

    NotificationDelivery add(NotificationPriority priority) {
        return create(priority, null, 3);
    }

    NotificationDelivery add(NotificationPriority priority, int maxAttempts) {
        return create(priority, null, maxAttempts);
    }

    NotificationDelivery add(NotificationPriority priority, LocalDateTime createdAt) {
        return create(priority, createdAt, 3);
    }

    synchronized NotificationDelivery get(Long id) {
        return deliveries.get(id);
    }

    synchronized NotificationDelivery snapshot(NotificationDelivery delivery) {
        NotificationDelivery copy = new NotificationDelivery(delivery.getNotification(), delivery.getType(), delivery.getMaxAttempts());

        ReflectionTestUtils.setField(copy, "id", delivery.getId());
        ReflectionTestUtils.setField(copy, "status", delivery.getStatus());
        ReflectionTestUtils.setField(copy, "attemptCount", delivery.getAttemptCount());
        copy.setLockedBy(delivery.getLockedBy());
        copy.setLockedAt(delivery.getLockedAt());
        copy.setSentAt(delivery.getSentAt());

        return copy;
    }

    synchronized void markProcessing(NotificationDelivery delivery, String lockedBy, LocalDateTime lockedAt) {
        delivery.setStatus(NotificationStatus.PROCESSING);
        delivery.setLockedBy(lockedBy);
        delivery.setLockedAt(lockedAt);
        delivery.incrementAttemptCount();
    }

    synchronized List<DeliveryAttempt> attempts() {
        return new ArrayList<>(attempts);
    }

    private synchronized NotificationDelivery create(NotificationPriority priority, LocalDateTime createdAt, int maxAttempts) {
        long id = nextId++;

        Notification notification = new Notification("Content " + id, priority);
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt != null ? createdAt : start.plusNanos(id * 1_000_000L));

        NotificationDelivery delivery = new NotificationDelivery(notification, NotificationType.EMAIL, maxAttempts);
        ReflectionTestUtils.setField(delivery, "id", id);

        deliveries.put(id, delivery);

        return delivery;
    }

    private synchronized int claim(Long id, NotificationStatus pending, NotificationStatus processing,
                                   String lockedBy, LocalDateTime lockedAt) {
        NotificationDelivery delivery = deliveries.get(id);

        if (delivery == null || delivery.getStatus() != pending || delivery.getAttemptCount() >= delivery.getMaxAttempts()) {
            return 0;
        }

        delivery.setStatus(processing);
        delivery.setLockedBy(lockedBy);
        delivery.setLockedAt(lockedAt);
        delivery.incrementAttemptCount();

        return 1;
    }

    private synchronized int finish(Long id, String lockedBy, NotificationStatus processing,
                                    NotificationStatus newStatus, LocalDateTime sentAt) {
        NotificationDelivery delivery = deliveries.get(id);

        if (delivery == null || delivery.getStatus() != processing || lockedBy == null
                || !lockedBy.equals(delivery.getLockedBy())) {
            return 0;
        }

        delivery.setStatus(newStatus);
        delivery.setSentAt(sentAt);
        delivery.setLockedBy(null);
        delivery.setLockedAt(null);

        return 1;
    }

    private synchronized List<Long> findNextIds(NotificationStatus status, NotificationPriority priority, Pageable pageable) {
        return deliveries.values().stream()
                .filter(delivery -> delivery.getStatus() == status)
                .filter(delivery -> delivery.getAttemptCount() < delivery.getMaxAttempts())
                .filter(delivery -> delivery.getNotification().getPriority() == priority)
                .sorted(QUEUE_ORDER)
                .limit(pageable.getPageSize())
                .map(NotificationDelivery::getId)
                .toList();
    }

    private synchronized List<Long> findAgedIds(NotificationStatus status, LocalDateTime agingCutoff, Pageable pageable) {
        return deliveries.values().stream()
                .filter(delivery -> delivery.getStatus() == status)
                .filter(delivery -> delivery.getAttemptCount() < delivery.getMaxAttempts())
                .filter(delivery -> !delivery.getNotification().getCreatedAt().isAfter(agingCutoff))
                .sorted(QUEUE_ORDER)
                .limit(pageable.getPageSize())
                .map(NotificationDelivery::getId)
                .toList();
    }

    private synchronized List<NotificationDelivery> findExpired(NotificationStatus status, LocalDateTime threshold) {
        return deliveries.values().stream()
                .filter(delivery -> delivery.getStatus() == status)
                .filter(delivery -> delivery.getLockedAt() != null && delivery.getLockedAt().isBefore(threshold))
                .toList();
    }

    private synchronized long count(NotificationStatus status) {
        return deliveries.values().stream()
                .filter(delivery -> delivery.getStatus() == status)
                .count();
    }

    private synchronized long count(NotificationStatus status, NotificationPriority priority) {
        return deliveries.values().stream()
                .filter(delivery -> delivery.getStatus() == status)
                .filter(delivery -> delivery.getNotification().getPriority() == priority)
                .count();
    }

    private synchronized List<NotificationDelivery> findProcessing(NotificationStatus status) {
        return deliveries.values().stream()
                .filter(delivery -> delivery.getStatus() == status)
                .sorted(Comparator.comparing(NotificationDelivery::getLockedAt))
                .toList();
    }

    private synchronized List<NotificationDelivery> findRecentlySent(NotificationStatus status) {
        return deliveries.values().stream()
                .filter(delivery -> delivery.getStatus() == status)
                .sorted(Comparator.comparing(NotificationDelivery::getSentAt).reversed())
                .limit(10)
                .toList();
    }

    private synchronized DeliveryAttempt record(DeliveryAttempt attempt) {
        attempts.add(attempt);

        return attempt;
    }

    private synchronized List<DeliveryAttempt> findAttempts(Long deliveryId) {
        return attempts.stream()
                .filter(attempt -> attempt.getDelivery().getId().equals(deliveryId))
                .sorted(Comparator.comparingInt(DeliveryAttempt::getAttemptNumber))
                .toList();
    }
}
