package pt.uportalegre.estgd.ei.es.notificationservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import pt.uportalegre.estgd.ei.es.notificationservice.model.*;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.DeliveryAttemptResult;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.EmailRecipientType;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.DeliveryAttemptRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.EmailDetailsRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.sender.NotificationSender;
import pt.uportalegre.estgd.ei.es.notificationservice.service.DeliveryProcessor;
import pt.uportalegre.estgd.ei.es.notificationservice.service.NotificationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryScenariosTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private EmailDetailsRepository emailDetailsRepository;

    @Mock
    private DeliveryAttemptRepository attemptRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private NotificationSender emailSender;

    private FakeDeliveryDatabase database;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        database = new FakeDeliveryDatabase(deliveryRepository, attemptRepository);

        DeliveryProcessor deliveryProcessor = new DeliveryProcessor(deliveryRepository, attemptRepository,
                List.of(emailSender), transactionManager, 10, 60, 300
        );

        notificationService = new NotificationService(notificationRepository, deliveryRepository,
                emailDetailsRepository, attemptRepository, deliveryProcessor, 3
        );
    }

    @Test
    void createEmailNotification_WithValidRecipients_Success() {
        String content = "Hello World";
        String subject = "Subject Test";
        List<EmailRecipient> recipients = List.of(
                new EmailRecipient("22999@uportalegre.pt", EmailRecipientType.TO),
                new EmailRecipient("23406@uportalegre.pt", EmailRecipientType.CC)
        );

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
        when(deliveryRepository.save(any(NotificationDelivery.class))).thenAnswer(i -> i.getArgument(0));

        Notification result = notificationService.createEmailNotification(content, subject, recipients,
                NotificationPriority.HIGH);

        assertNotNull(result);
        assertEquals(content, result.getContent());
        assertEquals(NotificationPriority.HIGH, result.getPriority());
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(deliveryRepository, times(1)).save(any(NotificationDelivery.class));
        verify(emailDetailsRepository, times(1)).save(any(EmailDetails.class));
    }

    @Test
    void createEmailNotification_WithoutToRecipient_ShouldThrowException() {
        List<EmailRecipient> recipients = List.of(
                new EmailRecipient("22999@uportalegre.pt", EmailRecipientType.CC)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                notificationService.createEmailNotification("Content", "Subject", recipients,
                        NotificationPriority.NORMAL)
        );

        assertEquals("At least one TO recipient is required.", ex.getMessage());
        verifyNoInteractions(notificationRepository, deliveryRepository, emailDetailsRepository);
    }

    @Test
    void sendDelivery_WhenSuccess_ShouldMarkAsSentAndSuccessAttempt() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);

        NotificationDelivery result = notificationService.sendDelivery(delivery.getId());

        assertEquals(NotificationStatus.SENT, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        assertNotNull(result.getSentAt());
        assertNull(result.getLockedBy());
        assertNull(result.getLockedAt());

        verify(emailSender, times(1)).send(delivery);

        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        assertEquals(DeliveryAttemptResult.SUCCESS, attemptCaptor.getValue().getResult());
        assertEquals(1, attemptCaptor.getValue().getAttemptNumber());
        assertNull(attemptCaptor.getValue().getErrorMessage());
    }

    @Test
    void sendDelivery_WhenSenderFailsAndAttemptsRemain_ShouldKeepPending() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);
        doThrow(new RuntimeException("Error connecting SMTP")).when(emailSender).send(delivery);

        NotificationDelivery result = notificationService.sendDelivery(delivery.getId());

        assertEquals(NotificationStatus.PENDING, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        assertNull(result.getSentAt());
        assertNull(result.getLockedBy());

        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        assertEquals(DeliveryAttemptResult.FAILURE, attemptCaptor.getValue().getResult());
        assertEquals("Error connecting SMTP", attemptCaptor.getValue().getErrorMessage());
    }

    @Test
    void sendDelivery_WhenSenderFailsOnLastAttempt_ShouldMarkAsFailed() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL, 1);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);
        doThrow(new RuntimeException("Timeout")).when(emailSender).send(delivery);

        NotificationDelivery result = notificationService.sendDelivery(delivery.getId());

        assertEquals(NotificationStatus.FAILED, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        assertNull(result.getSentAt());
    }

    @Test
    void sendDelivery_WhenFailsTwiceThenSucceeds_ShouldRecordEveryAttempt() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);
        doThrow(new RuntimeException("Error 1"))
                .doThrow(new RuntimeException("Error 2"))
                .doNothing()
                .when(emailSender).send(delivery);

        notificationService.sendDelivery(delivery.getId());
        notificationService.sendDelivery(delivery.getId());
        NotificationDelivery result = notificationService.sendDelivery(delivery.getId());

        assertEquals(NotificationStatus.SENT, result.getStatus());
        assertEquals(3, result.getAttemptCount());

        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptRepository, times(3)).save(attemptCaptor.capture());

        List<DeliveryAttempt> attempts = attemptCaptor.getAllValues();
        assertEquals(DeliveryAttemptResult.FAILURE, attempts.get(0).getResult());
        assertEquals(1, attempts.get(0).getAttemptNumber());
        assertEquals("Error 1", attempts.get(0).getErrorMessage());
        assertEquals(DeliveryAttemptResult.FAILURE, attempts.get(1).getResult());
        assertEquals(2, attempts.get(1).getAttemptNumber());
        assertEquals("Error 2", attempts.get(1).getErrorMessage());
        assertEquals(DeliveryAttemptResult.SUCCESS, attempts.get(2).getResult());
        assertEquals(3, attempts.get(2).getAttemptNumber());
    }

    @Test
    void sendDelivery_WhenAttemptsAreExhausted_ShouldNotSendAgain() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL, 1);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);
        doThrow(new RuntimeException("Timeout")).when(emailSender).send(delivery);

        notificationService.sendDelivery(delivery.getId());
        NotificationDelivery result = notificationService.sendDelivery(delivery.getId());

        assertEquals(NotificationStatus.FAILED, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        verify(emailSender, times(1)).send(delivery);
        verify(attemptRepository, times(1)).save(any(DeliveryAttempt.class));
    }

    @Test
    void sendDelivery_WhenAlreadySent_ShouldNotSendAgain() {
        NotificationDelivery delivery = database.add(NotificationPriority.NORMAL);

        when(emailSender.supports(NotificationType.EMAIL)).thenReturn(true);

        notificationService.sendDelivery(delivery.getId());
        NotificationDelivery result = notificationService.sendDelivery(delivery.getId());

        assertEquals(NotificationStatus.SENT, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        verify(emailSender, times(1)).send(delivery);
    }

    @Test
    void sendDelivery_WhenDeliveryDoesNotExist_ShouldThrowException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                notificationService.sendDelivery(99L)
        );

        assertEquals("Delivery not found: 99", ex.getMessage());
        verifyNoInteractions(emailSender);
    }
}
