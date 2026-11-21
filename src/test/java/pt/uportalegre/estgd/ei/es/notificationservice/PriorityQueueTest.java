package pt.uportalegre.estgd.ei.es.notificationservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.DeliveryAttemptRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.sender.NotificationSender;
import pt.uportalegre.estgd.ei.es.notificationservice.service.DeliveryProcessor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriorityQueueTest {

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private DeliveryAttemptRepository attemptRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private NotificationSender emailSender;

    private FakeDeliveryDatabase database;

    private DeliveryProcessor deliveryProcessor;

    @BeforeEach
    void setUp() {
        database = new FakeDeliveryDatabase(deliveryRepository, attemptRepository);

        deliveryProcessor = new DeliveryProcessor(deliveryRepository, attemptRepository,
                List.of(emailSender), transactionManager, 10, 60, 300
        );
    }

    @Test
    void claimNext_WhenSeveralPriorities_ShouldClaimHighBeforeNormalBeforeLow() {
        NotificationDelivery low = database.add(NotificationPriority.LOW);
        NotificationDelivery normal = database.add(NotificationPriority.NORMAL);
        NotificationDelivery high = database.add(NotificationPriority.HIGH);

        assertEquals(Optional.of(high.getId()), deliveryProcessor.claimNext("p1"));
        assertEquals(Optional.of(normal.getId()), deliveryProcessor.claimNext("p1"));
        assertEquals(Optional.of(low.getId()), deliveryProcessor.claimNext("p1"));
        assertEquals(Optional.empty(), deliveryProcessor.claimNext("p1"));
    }

    @Test
    void claimNext_WhenDeliveryIsClaimed_ShouldMarkAsProcessingAndCountTheAttempt() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL);

        Optional<Long> claimed = deliveryProcessor.claimNext("p1");

        assertEquals(Optional.of(delivery.getId()), claimed);
        assertEquals(NotificationStatus.PROCESSING, delivery.getStatus());
        assertEquals("p1", delivery.getLockedBy());
        assertNotNull(delivery.getLockedAt());
        assertEquals(1, delivery.getAttemptCount());
    }

    @Test
    void claimNext_WhenSamePriority_ShouldClaimOldestNotificationFirst() {
        NotificationDelivery first = database.add(NotificationPriority.NORMAL);
        NotificationDelivery second = database.add(NotificationPriority.NORMAL);
        NotificationDelivery oldest = database.add(NotificationPriority.NORMAL, LocalDateTime.now().minusSeconds(100));

        assertEquals(Optional.of(oldest.getId()), deliveryProcessor.claimNext("p1"));
        assertEquals(Optional.of(first.getId()), deliveryProcessor.claimNext("p1"));
        assertEquals(Optional.of(second.getId()), deliveryProcessor.claimNext("p1"));
    }

    @Test
    void processNext_WhenManyNotificationsExist_ShouldSendByPriorityThenByArrival() {
        List<Long> high = new ArrayList<>();
        List<Long> normal = new ArrayList<>();
        List<Long> low = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            low.add(database.add(NotificationPriority.LOW).getId());
            normal.add(database.add(NotificationPriority.NORMAL).getId());
            high.add(database.add(NotificationPriority.HIGH).getId());
        }

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);

        int processed = 0;

        while (deliveryProcessor.processNext("p1")) {
            processed++;
        }

        List<Long> expected = new ArrayList<>(high);
        expected.addAll(normal);
        expected.addAll(low);

        assertEquals(30, processed);
        assertEquals(expected, sentIds(30));
    }

    @Test
    void processNext_WhenHighArrivesWhileNormalArePending_ShouldProcessHighNext() {
        NotificationDelivery normal1 = database.add(NotificationPriority.NORMAL);
        NotificationDelivery normal2 = database.add(NotificationPriority.NORMAL);
        NotificationDelivery normal3 = database.add(NotificationPriority.NORMAL);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);

        assertTrue(deliveryProcessor.processNext("p1"));

        NotificationDelivery high = database.add(NotificationPriority.HIGH);

        int processed = 0;

        while (deliveryProcessor.processNext("p1")) {
            processed++;
        }

        assertEquals(3, processed);
        assertEquals(List.of(normal1.getId(), high.getId(), normal2.getId(), normal3.getId()), sentIds(4));
    }

    @Test
    void claimNext_WhenLowIsOlderThanAgingThreshold_ShouldClaimItBeforeHigh() {
        NotificationDelivery high = database.add(NotificationPriority.HIGH);
        NotificationDelivery agedLow = database.add(NotificationPriority.LOW, LocalDateTime.now().minusSeconds(600));

        assertEquals(Optional.of(agedLow.getId()), deliveryProcessor.claimNext("p1"));
        assertEquals(Optional.of(high.getId()), deliveryProcessor.claimNext("p1"));
    }

    @Test
    void claimNext_WhenSeveralDeliveriesAreAged_ShouldClaimOldestFirstRegardlessOfPriority() {
        NotificationDelivery agedNormal = database.add(NotificationPriority.NORMAL, LocalDateTime.now().minusSeconds(400));
        NotificationDelivery agedHigh = database.add(NotificationPriority.HIGH, LocalDateTime.now().minusSeconds(500));
        NotificationDelivery agedLow = database.add(NotificationPriority.LOW, LocalDateTime.now().minusSeconds(900));

        assertEquals(Optional.of(agedLow.getId()), deliveryProcessor.claimNext("p1"));
        assertEquals(Optional.of(agedHigh.getId()), deliveryProcessor.claimNext("p1"));
        assertEquals(Optional.of(agedNormal.getId()), deliveryProcessor.claimNext("p1"));
    }

    @Test
    void claimNext_WhenQueueIsEmpty_ShouldCheckAgedThenPrioritiesByWeight() {
        assertEquals(Optional.empty(), deliveryProcessor.claimNext("p1"));

        InOrder inOrder = inOrder(deliveryRepository);
        inOrder.verify(deliveryRepository).findAgedIds(eq(NotificationStatus.PENDING), any(LocalDateTime.class), any(Pageable.class));
        inOrder.verify(deliveryRepository).findNextIdsByPriority(eq(NotificationStatus.PENDING), eq(NotificationPriority.HIGH), any(Pageable.class));
        inOrder.verify(deliveryRepository).findNextIdsByPriority(eq(NotificationStatus.PENDING), eq(NotificationPriority.NORMAL), any(Pageable.class));
        inOrder.verify(deliveryRepository).findNextIdsByPriority(eq(NotificationStatus.PENDING), eq(NotificationPriority.LOW), any(Pageable.class));
    }

    @Test
    void claimNext_ShouldRequestOnlyTheConfiguredWindowOfCandidates() {
        deliveryProcessor.claimNext("p1");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryRepository).findAgedIds(any(), any(), pageableCaptor.capture());

        assertEquals(10, pageableCaptor.getValue().getPageSize());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    @Test
    void claimNext_WhenDeliveriesAreNotClaimable_ShouldIgnoreThem() {
        NotificationDelivery sent = database.add(NotificationPriority.HIGH);
        sent.setStatus(NotificationStatus.SENT);

        NotificationDelivery failed = database.add(NotificationPriority.HIGH);
        failed.setStatus(NotificationStatus.FAILED);

        NotificationDelivery processing = database.add(NotificationPriority.HIGH);
        database.markProcessing(processing, "other/worker-1", LocalDateTime.now());

        NotificationDelivery exhausted = database.add(NotificationPriority.HIGH, 1);
        exhausted.incrementAttemptCount();

        assertEquals(Optional.empty(), deliveryProcessor.claimNext("p1"));
    }

    @Test
    void claimNext_WhenFirstCandidateWasClaimedByAnotherProcessor_ShouldTryTheNextOne() {
        NotificationDelivery taken = database.add(NotificationPriority.HIGH);
        NotificationDelivery free = database.add(NotificationPriority.HIGH);

        database.markProcessing(taken, "other/worker-1", LocalDateTime.now());

        doReturn(List.of(taken.getId(), free.getId())).when(deliveryRepository)
                .findNextIdsByPriority(eq(NotificationStatus.PENDING), eq(NotificationPriority.HIGH), any(Pageable.class));

        assertEquals(Optional.of(free.getId()), deliveryProcessor.claimNext("p1"));
        assertEquals("other/worker-1", taken.getLockedBy());
        assertEquals(1, taken.getAttemptCount());
        assertEquals("p1", free.getLockedBy());
    }

    @Test
    void processNext_WhenHighFailsAndAttemptsRemain_ShouldRetryItBeforeLowerPriorities() {
        NotificationDelivery normal = database.add(NotificationPriority.NORMAL);
        NotificationDelivery high = database.add(NotificationPriority.HIGH);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);
        doThrow(new RuntimeException("Error connecting SMTP")).doNothing().when(emailSender).send(high);

        int processed = 0;

        while (deliveryProcessor.processNext("p1")) {
            processed++;
        }

        assertEquals(3, processed);
        assertEquals(List.of(high.getId(), high.getId(), normal.getId()), sentIds(3));
        assertEquals(NotificationStatus.SENT, high.getStatus());
        assertEquals(2, high.getAttemptCount());
        assertEquals(NotificationStatus.SENT, normal.getStatus());
    }

    private List<Long> sentIds(int expectedSends) {
        ArgumentCaptor<NotificationDelivery> deliveryCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(emailSender, times(expectedSends)).send(deliveryCaptor.capture());

        return deliveryCaptor.getAllValues().stream()
                .map(NotificationDelivery::getId)
                .toList();
    }
}
