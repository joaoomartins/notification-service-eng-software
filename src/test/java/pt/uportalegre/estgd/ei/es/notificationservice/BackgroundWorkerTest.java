package pt.uportalegre.estgd.ei.es.notificationservice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.uportalegre.estgd.ei.es.notificationservice.service.DeliveryProcessor;
import pt.uportalegre.estgd.ei.es.notificationservice.worker.BackgroundWorker;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackgroundWorkerTest {

    @Mock
    private DeliveryProcessor deliveryProcessor;

    private BackgroundWorker worker;

    @AfterEach
    void tearDown() {
        if (worker != null) {
            worker.shutdown();
        }
    }

    @Test
    void processPendingDeliveries_ShouldRecoverExpiredLocksAndStartProcessing() {
        worker = new BackgroundWorker(deliveryProcessor, 2);

        when(deliveryProcessor.recoverExpiredLocks()).thenReturn(2);

        worker.processPendingDeliveries();

        verify(deliveryProcessor, times(1)).recoverExpiredLocks();
        verify(deliveryProcessor, timeout(5000).atLeastOnce()).processNext(any());
    }

    @Test
    void processPendingDeliveries_WhenRecoveryFails_ShouldStillStartProcessing() {
        worker = new BackgroundWorker(deliveryProcessor, 1);

        when(deliveryProcessor.recoverExpiredLocks()).thenThrow(new RuntimeException("Database unavailable"));

        assertDoesNotThrow(() -> worker.processPendingDeliveries());

        verify(deliveryProcessor, timeout(5000).atLeastOnce()).processNext(any());
    }

    @Test
    void processPendingDeliveries_ShouldReturnWithoutWaitingForTheProcessing() throws Exception {
        worker = new BackgroundWorker(deliveryProcessor, 1);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(deliveryProcessor.processNext(any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return false;
        });

        worker.processPendingDeliveries();

        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertEquals(1, release.getCount());

        release.countDown();
    }

    @Test
    void processPendingDeliveries_ShouldRunOneProcessorPerThread() throws Exception {
        int threads = 3;
        worker = new BackgroundWorker(deliveryProcessor, threads);

        CountDownLatch allRunning = new CountDownLatch(threads);
        Set<String> threadNames = ConcurrentHashMap.newKeySet();

        when(deliveryProcessor.processNext(any())).thenAnswer(invocation -> {
            threadNames.add(Thread.currentThread().getName());
            allRunning.countDown();
            allRunning.await(10, TimeUnit.SECONDS);
            return false;
        });

        worker.processPendingDeliveries();

        assertTrue(allRunning.await(5, TimeUnit.SECONDS));
        assertEquals(threads, threadNames.size());
    }

    @Test
    void processPendingDeliveries_WhenCalledAgainAfterTheQueueWasDrained_ShouldStartProcessingAgain() {
        worker = new BackgroundWorker(deliveryProcessor, 1);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            worker.processPendingDeliveries();

            verify(deliveryProcessor, atLeast(2)).processNext(any());
        });
    }

    @Test
    void constructor_WhenThreadsIsLessThanOne_ShouldThrowException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new BackgroundWorker(deliveryProcessor, 0)
        );

        assertEquals("notifications.worker.threads must be at least 1.", ex.getMessage());
    }
}
