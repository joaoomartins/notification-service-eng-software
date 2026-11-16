package pt.uportalegre.estgd.ei.es.notificationservice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import pt.uportalegre.estgd.ei.es.notificationservice.dto.DeliveryAttemptDto;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.DeliveryDetailsDto;
import pt.uportalegre.estgd.ei.es.notificationservice.model.Notification;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.DeliveryAttemptResult;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.DeliveryAttemptRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.EmailDetailsRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.service.NotificationService;
import pt.uportalegre.estgd.ei.es.notificationservice.simulation.SimulatedEmailSender;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the 4 delivery scenarios asked in Phase 2.
 *
 * - "test" profile: uses an in-memory H2 database and turns OFF the BackgroundWorker.
 * - "simulation" profile: uses SimulatedEmailSender, so no real email is sent.
 *
 * Because the worker is off, each test calls sendDelivery() by hand.
 * Each call is like one cycle of the worker (one attempt).
 * The attempt limit is 3 (see application-test.properties).
 */
@SpringBootTest
@ActiveProfiles({"test", "simulation"})
class DeliveryScenariosTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SimulatedEmailSender emailSender;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private EmailDetailsRepository emailDetailsRepository;

    @Autowired
    private DeliveryAttemptRepository attemptRepository;

    @BeforeEach
    void setUp() {
        // Every test starts with no failures configured.
        emailSender.reset();
    }

    @AfterEach
    void cleanUp() {
        // Clean the database so tests do not affect each other.
        // Children are deleted first because of the foreign keys.
        attemptRepository.deleteAll();
        emailDetailsRepository.deleteAll();
        deliveryRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    // Scenario 1: the email is sent on the first attempt.
    @Test
    void successfulDelivery() {
        Long deliveryId = createDelivery();

        notificationService.sendDelivery(deliveryId);

        DeliveryDetailsDto delivery = notificationService.findDeliveryDetailsById(deliveryId);
        assertEquals(NotificationStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNotNull(delivery.getSentAt());
        assertEquals(List.of(DeliveryAttemptResult.SUCCESS), getResults(delivery));
        assertEquals(1, emailSender.getSentCount());
    }

    // Scenario 2: the first attempt fails and the second one works.
    @Test
    void failureFollowedBySuccess() {
        emailSender.configure(1, false); // only the next attempt fails
        Long deliveryId = createDelivery();

        // Attempt 1 fails: the delivery stays PENDING, so it will be retried.
        notificationService.sendDelivery(deliveryId);

        DeliveryDetailsDto afterFailure = notificationService.findDeliveryDetailsById(deliveryId);
        assertEquals(NotificationStatus.PENDING, afterFailure.getStatus());
        assertTrue(afterFailure.getLastErrorMessage().contains("Simulated email failure"));

        // Attempt 2 works.
        notificationService.sendDelivery(deliveryId);

        DeliveryDetailsDto delivery = notificationService.findDeliveryDetailsById(deliveryId);
        assertEquals(NotificationStatus.SENT, delivery.getStatus());
        assertEquals(2, delivery.getAttemptCount());
        assertEquals(List.of(DeliveryAttemptResult.FAILURE, DeliveryAttemptResult.SUCCESS), getResults(delivery));
        assertEquals(1, emailSender.getSentCount());
    }

    // Scenario 3: several failures in a row, then a success on the last allowed attempt.
    @Test
    void consecutiveFailures() {
        emailSender.configure(2, false); // the next 2 attempts fail
        Long deliveryId = createDelivery();

        notificationService.sendDelivery(deliveryId);
        notificationService.sendDelivery(deliveryId);

        // After 2 failures the notification was NOT lost: it is still PENDING
        // and the worker query still finds it.
        DeliveryDetailsDto afterFailures = notificationService.findDeliveryDetailsById(deliveryId);
        assertEquals(NotificationStatus.PENDING, afterFailures.getStatus());
        assertEquals(List.of(DeliveryAttemptResult.FAILURE, DeliveryAttemptResult.FAILURE), getResults(afterFailures));
        assertTrue(isWaitingForWorker(deliveryId));

        // Attempt 3 works.
        notificationService.sendDelivery(deliveryId);

        DeliveryDetailsDto delivery = notificationService.findDeliveryDetailsById(deliveryId);
        assertEquals(NotificationStatus.SENT, delivery.getStatus());
        assertEquals(
                List.of(DeliveryAttemptResult.FAILURE, DeliveryAttemptResult.FAILURE, DeliveryAttemptResult.SUCCESS),
                getResults(delivery));
    }

    // Scenario 4: every attempt fails, so the delivery reaches the maximum (3) and FAILS.
    @Test
    void maximumAttemptsReached() {
        emailSender.configure(0, true); // every attempt fails
        Long deliveryId = createDelivery();

        notificationService.sendDelivery(deliveryId);
        notificationService.sendDelivery(deliveryId);
        notificationService.sendDelivery(deliveryId);

        DeliveryDetailsDto delivery = notificationService.findDeliveryDetailsById(deliveryId);
        assertEquals(NotificationStatus.FAILED, delivery.getStatus());
        assertEquals(3, delivery.getAttemptCount());
        assertNull(delivery.getSentAt());
        assertEquals(
                List.of(DeliveryAttemptResult.FAILURE, DeliveryAttemptResult.FAILURE, DeliveryAttemptResult.FAILURE),
                getResults(delivery));

        // A FAILED delivery is no longer picked up by the worker...
        assertFalse(isWaitingForWorker(deliveryId));

        // ...and calling sendDelivery again does nothing (no 4th attempt).
        notificationService.sendDelivery(deliveryId);
        assertEquals(3, notificationService.findDeliveryDetailsById(deliveryId).getAttemptCount());

        // Nothing was sent, but the notification is still saved (not lost).
        assertEquals(0, emailSender.getSentCount());
        assertEquals(1, notificationRepository.count());
    }

    // ---------- helpers ----------

    // Creates a notification (like the web form does) and returns the id of its delivery.
    private Long createDelivery() {
        Notification notification = notificationService.createEmailNotificationFromStrings(
                "Test content", "Test subject", "to@test.pt", "", "");

        List<DeliveryDetailsDto> deliveries = notificationService.findDeliveryDetailsByNotificationId(notification.getId());
        return deliveries.get(0).getId();
    }

    // Returns the result of each attempt, in order (e.g. [FAILURE, SUCCESS]).
    private List<DeliveryAttemptResult> getResults(DeliveryDetailsDto delivery) {
        List<DeliveryAttemptResult> results = new ArrayList<>();
        for (DeliveryAttemptDto attempt : delivery.getAttempts()) {
            results.add(attempt.getResult());
        }
        return results;
    }

    // True if the BackgroundWorker would pick this delivery in its next cycle.
    private boolean isWaitingForWorker(Long deliveryId) {
        for (NotificationDelivery delivery : deliveryRepository.findEligibleDeliveries(NotificationStatus.PENDING)) {
            if (delivery.getId().equals(deliveryId)) {
                return true;
            }
        }
        return false;
    }
}
