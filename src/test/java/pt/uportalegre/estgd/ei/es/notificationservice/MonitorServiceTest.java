package pt.uportalegre.estgd.ei.es.notificationservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.MonitorItemDto;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.QueueSummaryDto;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.DeliveryAttemptRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.sender.NotificationSender;
import pt.uportalegre.estgd.ei.es.notificationservice.service.MonitorService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private DeliveryAttemptRepository attemptRepository;

    @Mock
    private NotificationSender emailSender;

    private FakeDeliveryDatabase database;

    private MonitorService monitorService;

    @BeforeEach
    void setUp() {
        database = new FakeDeliveryDatabase(deliveryRepository, attemptRepository);
        monitorService = new MonitorService(deliveryRepository);
    }

    @Test
    void findQueueSummary_WhenDeliveriesAreInDifferentStates_ShouldCountByStatusAndPriority() {
        database.add(NotificationPriority.HIGH);
        database.add(NotificationPriority.HIGH);
        database.add(NotificationPriority.NORMAL);
        database.add(NotificationPriority.LOW);
        database.add(NotificationPriority.LOW);
        database.add(NotificationPriority.LOW);

        NotificationDelivery processing = database.add(NotificationPriority.NORMAL);
        database.markProcessing(processing, "instance/worker-1", LocalDateTime.now());

        NotificationDelivery sent = database.add(NotificationPriority.HIGH);
        sent.setStatus(NotificationStatus.SENT);
        sent.setSentAt(LocalDateTime.now());

        NotificationDelivery failed = database.add(NotificationPriority.LOW);
        failed.setStatus(NotificationStatus.FAILED);

        QueueSummaryDto summary = monitorService.findQueueSummary();

        assertEquals(6, summary.getPending());
        assertEquals(2, summary.getPendingHigh());
        assertEquals(1, summary.getPendingNormal());
        assertEquals(3, summary.getPendingLow());
        assertEquals(1, summary.getProcessing());
        assertEquals(1, summary.getSent());
        assertEquals(1, summary.getFailed());
    }

    @Test
    void findQueueSummary_WhenDeliveryIsBeingProcessed_ShouldShowWhoIsProcessingIt() {
        NotificationDelivery delivery = database.add(NotificationPriority.HIGH);
        LocalDateTime lockedAt = LocalDateTime.now();
        database.markProcessing(delivery, "instance/worker-1", lockedAt);

        QueueSummaryDto summary = monitorService.findQueueSummary();

        assertEquals(1, summary.getProcessingItems().size());

        MonitorItemDto item = summary.getProcessingItems().get(0);
        assertEquals(delivery.getId(), item.getDeliveryId());
        assertEquals(delivery.getNotification().getId(), item.getNotificationId());
        assertEquals(NotificationPriority.HIGH, item.getPriority());
        assertEquals("instance/worker-1", item.getProcessor());
        assertEquals(lockedAt, item.getLockedAt());
        assertEquals(1, item.getAttemptCount());
        assertEquals(3, item.getMaxAttempts());

        verifyNoInteractions(emailSender);
        assertEquals(NotificationStatus.PROCESSING, delivery.getStatus());
    }

    @Test
    void findQueueSummary_WhenManyDeliveriesWereSent_ShouldListOnlyTheTenMostRecent() {
        LocalDateTime base = LocalDateTime.now().minusMinutes(30);
        List<NotificationDelivery> sentDeliveries = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            NotificationDelivery delivery = database.add(NotificationPriority.NORMAL);
            delivery.setStatus(NotificationStatus.SENT);
            delivery.setSentAt(base.plusMinutes(i));
            sentDeliveries.add(delivery);
        }

        QueueSummaryDto summary = monitorService.findQueueSummary();

        assertEquals(12, summary.getSent());
        assertEquals(10, summary.getRecentlySent().size());
        assertEquals(sentDeliveries.get(11).getId(), summary.getRecentlySent().get(0).getDeliveryId());
        assertEquals(sentDeliveries.get(2).getId(), summary.getRecentlySent().get(9).getDeliveryId());
    }
}
