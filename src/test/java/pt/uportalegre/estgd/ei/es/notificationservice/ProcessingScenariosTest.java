package pt.uportalegre.estgd.ei.es.notificationservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import pt.uportalegre.estgd.ei.es.notificationservice.model.DeliveryAttempt;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.DeliveryAttemptResult;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.DeliveryAttemptRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.sender.NotificationSender;
import pt.uportalegre.estgd.ei.es.notificationservice.service.DeliveryProcessor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessingScenariosTest {

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
        deliveryProcessor = newProcessor();
    }

    @Test
    void claim_WhenTwoProcessorsClaimTheSameDelivery_ShouldOnlyTheFirstSucceed() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL);

        assertTrue(deliveryProcessor.claim(delivery.getId(), "p1"));
        assertFalse(deliveryProcessor.claim(delivery.getId(), "p2"));

        assertEquals(NotificationStatus.PROCESSING, delivery.getStatus());
        assertEquals("p1", delivery.getLockedBy());
        assertEquals(1, delivery.getAttemptCount());
    }

    @Test
    void claim_WhenManyProcessorsClaimAtTheSameTime_ShouldOnlyOneSucceed() throws Exception {
        NotificationDelivery delivery = database.add(NotificationPriority.HIGH);
        AtomicInteger winners = new AtomicInteger();

        runConcurrently(8, index -> {
            if (deliveryProcessor.claim(delivery.getId(), "p" + index)) {
                winners.incrementAndGet();
            }
        });

        assertEquals(1, winners.get());
        assertEquals(NotificationStatus.PROCESSING, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
    }

    @Test
    void processNext_WhenManyProcessorsRunAtTheSameTime_ShouldSendEachDeliveryExactlyOnce() throws Exception {
        List<NotificationDelivery> deliveries = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            deliveries.add(database.add(NotificationPriority.HIGH));
            deliveries.add(database.add(NotificationPriority.NORMAL));
            deliveries.add(database.add(NotificationPriority.LOW));
        }

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);

        AtomicInteger processed = new AtomicInteger();

        runConcurrently(6, index -> {
            String processorId = "p" + index;

            while (deliveryProcessor.processNext(processorId)) {
                processed.incrementAndGet();
            }
        });

        assertEquals(60, processed.get());

        ArgumentCaptor<NotificationDelivery> deliveryCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(emailSender, times(60)).send(deliveryCaptor.capture());

        Set<Long> sentIds = new HashSet<>();
        deliveryCaptor.getAllValues().forEach(delivery -> sentIds.add(delivery.getId()));

        assertEquals(60, sentIds.size());

        for (NotificationDelivery delivery : deliveries) {
            assertEquals(NotificationStatus.SENT, delivery.getStatus());
            assertEquals(1, delivery.getAttemptCount());
            assertNull(delivery.getLockedBy());
        }

        assertEquals(60, database.attempts().size());
        assertTrue(database.attempts().stream().allMatch(attempt -> attempt.getResult() == DeliveryAttemptResult.SUCCESS));
    }

    @Test
    void currentProcessorId_WhenCalledFromDifferentThreads_ShouldIdentifyEachThread() throws Exception {
        String mainThreadId = deliveryProcessor.currentProcessorId();

        ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "worker-x"));

        try {
            String otherThreadId = pool.submit(() -> deliveryProcessor.currentProcessorId()).get(5, TimeUnit.SECONDS);

            assertNotEquals(mainThreadId, otherThreadId);
            assertTrue(otherThreadId.endsWith("/worker-x"));
            assertEquals(mainThreadId.substring(0, mainThreadId.indexOf('/')), otherThreadId.substring(0, otherThreadId.indexOf('/')));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void processNext_WhenOneDeliveryFails_ShouldContinueWithTheNextOne() {
        NotificationDelivery failing = database.add(NotificationPriority.HIGH, 1);
        NotificationDelivery healthy = database.add(NotificationPriority.HIGH);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);
        doThrow(new RuntimeException("Timeout")).when(emailSender).send(failing);

        assertTrue(deliveryProcessor.processNext("p1"));
        assertTrue(deliveryProcessor.processNext("p1"));
        assertFalse(deliveryProcessor.processNext("p1"));

        assertEquals(NotificationStatus.FAILED, failing.getStatus());
        assertEquals(NotificationStatus.SENT, healthy.getStatus());
    }

    @Test
    void process_WhenLockWasLostDuringSend_ShouldDiscardTheResult() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);
        doAnswer(invocation -> {
            delivery.setLockedBy("other/worker-1");
            return null;
        }).when(emailSender).send(delivery);

        assertTrue(deliveryProcessor.claim(delivery.getId(), "p1"));
        assertFalse(deliveryProcessor.process(delivery.getId(), "p1"));

        assertEquals(NotificationStatus.PROCESSING, delivery.getStatus());
        assertEquals("other/worker-1", delivery.getLockedBy());
        verify(attemptRepository, never()).save(any(DeliveryAttempt.class));
    }

    @Test
    void process_WhenDeliveryIsNotClaimedByTheProcessor_ShouldNotSend() {
        NotificationDelivery pending = database.add(NotificationPriority.NORMAL);
        NotificationDelivery taken = database.add(NotificationPriority.NORMAL);

        database.markProcessing(taken, "other/worker-1", LocalDateTime.now());

        assertFalse(deliveryProcessor.process(pending.getId(), "p1"));
        assertFalse(deliveryProcessor.process(taken.getId(), "p1"));

        verifyNoInteractions(emailSender);
        assertEquals(NotificationStatus.PENDING, pending.getStatus());
        assertEquals("other/worker-1", taken.getLockedBy());
    }

    @Test
    void recoverExpiredLocks_WhenInstanceDiedDuringProcessing_ShouldReleaseDeliveryAndRecordInterruption() {
        NotificationDelivery delivery = database.add(NotificationPriority.HIGH);
        database.markProcessing(delivery, "dead-instance/worker-1", LocalDateTime.now().minusSeconds(120));

        int recovered = deliveryProcessor.recoverExpiredLocks();

        assertEquals(1, recovered);
        assertEquals(NotificationStatus.PENDING, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNull(delivery.getLockedBy());
        assertNull(delivery.getLockedAt());

        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        assertEquals(DeliveryAttemptResult.FAILURE, attemptCaptor.getValue().getResult());
        assertEquals(1, attemptCaptor.getValue().getAttemptNumber());
        assertEquals("Processing interrupted: the lock held by dead-instance/worker-1 expired.",
                attemptCaptor.getValue().getErrorMessage());
    }

    @Test
    void recoverExpiredLocks_WhenLockIsStillValid_ShouldKeepDeliveryProcessing() {
        NotificationDelivery delivery = database.add(NotificationPriority.HIGH);
        database.markProcessing(delivery, "other/worker-1", LocalDateTime.now().minusSeconds(10));

        int recovered = deliveryProcessor.recoverExpiredLocks();

        assertEquals(0, recovered);
        assertEquals(NotificationStatus.PROCESSING, delivery.getStatus());
        assertEquals("other/worker-1", delivery.getLockedBy());
        verify(attemptRepository, never()).save(any(DeliveryAttempt.class));
    }

    @Test
    void recoverExpiredLocks_WhenAttemptsAreExhausted_ShouldMarkAsFailed() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL, 1);
        database.markProcessing(delivery, "dead-instance/worker-1", LocalDateTime.now().minusSeconds(120));

        int recovered = deliveryProcessor.recoverExpiredLocks();

        assertEquals(1, recovered);
        assertEquals(NotificationStatus.FAILED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNull(delivery.getLockedBy());
        assertEquals(Optional.empty(), deliveryProcessor.claimNext("p1"));
    }

    @Test
    void recoverExpiredLocks_WhenAnotherInstanceAlreadyReleasedIt_ShouldNotRecordTwice() {
        NotificationDelivery delivery = database.add(NotificationPriority.HIGH);
        database.markProcessing(delivery, "dead-instance/worker-1", LocalDateTime.now().minusSeconds(120));

        DeliveryProcessor otherInstance = newProcessor();
        NotificationDelivery staleView = database.snapshot(delivery);

        assertEquals(1, deliveryProcessor.recoverExpiredLocks());

        doReturn(List.of(staleView)).when(deliveryRepository)
                .findByStatusAndLockedAtBefore(any(), any());

        assertEquals(0, otherInstance.recoverExpiredLocks());
        assertEquals(1, database.attempts().size());
        assertEquals(NotificationStatus.PENDING, delivery.getStatus());
    }

    @Test
    void processNext_AfterRestart_ShouldSendTheInterruptedDeliveryAgain() {
        NotificationDelivery delivery = database.add(NotificationPriority.HIGH);

        assertTrue(deliveryProcessor.claim(delivery.getId(), deliveryProcessor.currentProcessorId()));
        delivery.setLockedAt(LocalDateTime.now().minusSeconds(120));

        DeliveryProcessor restarted = newProcessor();

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);

        assertEquals(1, restarted.recoverExpiredLocks());
        assertTrue(restarted.processNext(restarted.currentProcessorId()));

        assertEquals(NotificationStatus.SENT, delivery.getStatus());
        assertEquals(2, delivery.getAttemptCount());

        List<DeliveryAttempt> attempts = database.attempts();
        assertEquals(2, attempts.size());
        assertEquals(DeliveryAttemptResult.FAILURE, attempts.get(0).getResult());
        assertEquals(1, attempts.get(0).getAttemptNumber());
        assertEquals(DeliveryAttemptResult.SUCCESS, attempts.get(1).getResult());
        assertEquals(2, attempts.get(1).getAttemptNumber());
    }

    private DeliveryProcessor newProcessor() {
        return new DeliveryProcessor(deliveryRepository, attemptRepository,
                List.of(emailSender), transactionManager, 10, 60, 300
        );
    }

    private void runConcurrently(int threads, ConcurrentTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                int index = i;

                Callable<Void> work = () -> {
                    ready.countDown();
                    start.await();
                    task.run(index);
                    return null;
                };

                futures.add(pool.submit(work));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ConcurrentTask {
        void run(int index) throws Exception;
    }
}
