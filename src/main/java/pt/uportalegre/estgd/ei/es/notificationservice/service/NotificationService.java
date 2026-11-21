
package pt.uportalegre.estgd.ei.es.notificationservice.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pt.uportalegre.estgd.ei.es.notificationservice.dto.DeliveryAttemptDto;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.DeliveryDetailsDto;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.EmailDetailsDto;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.NotificationListItem;
import pt.uportalegre.estgd.ei.es.notificationservice.model.*;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.*;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.DeliveryAttemptRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.EmailDetailsRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.sender.NotificationSender;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDetailsRepository emailDetailsRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final List<NotificationSender> senders;
    private final int maxAttempts;

    public NotificationService(NotificationRepository notificationRepository, NotificationDeliveryRepository deliveryRepository, EmailDetailsRepository emailDetailsRepository,
            DeliveryAttemptRepository attemptRepository, List<NotificationSender> senders,
            @Value("${notifications.delivery.max-attempts:3}") int maxAttempts) {

        // Read from application.properties (notifications.delivery.max-attempts).
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("notifications.delivery.max-attempts must be at least 1.");
        }

        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.emailDetailsRepository = emailDetailsRepository;
        this.attemptRepository = attemptRepository;
        this.senders = senders;
        this.maxAttempts = maxAttempts;
    }

    public Page<Notification> findPaginated(Pageable pageable) {
        return notificationRepository.findAll(pageable);
    }

    public Notification findNotificationById(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
    }

    public List<NotificationDelivery> findDeliveriesByNotificationId(Long notificationId) {
        return deliveryRepository.findByNotificationId(notificationId);
    }

    // Creates a notification and its email delivery (status PENDING).
    // The email is NOT sent here: the BackgroundWorker sends it later,
    // so the user does not have to wait on the page.
    // The attempt limit comes from application.properties, not from the user.
    @Transactional
    public Notification createEmailNotification(String content, String subject, List<EmailRecipient> recipients, NotificationPriority priority) {

        if (recipients == null || recipients.stream().noneMatch(
                recipient -> recipient.getRecipientType() == EmailRecipientType.TO)) {
            throw new IllegalArgumentException("At least one TO recipient is required.");
        }

        Notification notification = new Notification(content, priority);
        notification = notificationRepository.save(notification);

        NotificationDelivery delivery = new NotificationDelivery(
                notification,
                NotificationType.EMAIL,
                this.maxAttempts);

        delivery = deliveryRepository.save(delivery);

        EmailDetails emailDetails = new EmailDetails(delivery, subject);
        emailDetails.getRecipients().addAll(recipients);

        emailDetailsRepository.save(emailDetails);

        return notification;
    }

    @Transactional
    public Notification createEmailNotificationFromStrings(String content, String subject, String to, String cc, String bcc, NotificationPriority priority) {

        List<EmailRecipient> recipients = new ArrayList<>();

        recipients.addAll(parseRecipients(to, EmailRecipientType.TO));
        recipients.addAll(parseRecipients(cc, EmailRecipientType.CC));
        recipients.addAll(parseRecipients(bcc, EmailRecipientType.BCC));

        // The "at least one TO" rule is validated once, in createEmailNotification.
        return createEmailNotification(content, subject, recipients, priority);
    }

    // Called by the background worker to process one delivery (one attempt per call).
    @Transactional
    public NotificationDelivery sendDelivery(Long deliveryId) {

        NotificationDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));

        // Only PENDING deliveries are processed.
        // SENT must never be sent again and FAILED is a final state.
        if (delivery.getStatus() != NotificationStatus.PENDING) {
            return delivery;
        }

        // Safety check: never go over the attempt limit.
        if (delivery.getAttemptCount() >= delivery.getMaxAttempts()) {
            delivery.setStatus(NotificationStatus.FAILED);
            return deliveryRepository.save(delivery);
        }

        // Count this attempt BEFORE calling the sender.
        delivery.incrementAttemptCount();
        int attemptNumber = delivery.getAttemptCount();

        try {
            NotificationSender sender = findSender(delivery);
            sender.send(delivery);

            // The email was sent: mark the delivery as SENT and save a successful attempt.
            delivery.setStatus(NotificationStatus.SENT);
            delivery.setSentAt(LocalDateTime.now());

            attemptRepository.save(new DeliveryAttempt(delivery, attemptNumber, DeliveryAttemptResult.SUCCESS, null));

        } catch (Exception exception) {
            // We catch ANY exception, not only mail exceptions.
            // If an exception left this method, the transaction would be rolled back:
            // the attempt would not be saved and the worker would retry forever.

            delivery.setSentAt(null);

            // If there are attempts left, keep it PENDING so the worker tries again.
            // Otherwise, the delivery has FAILED for good.
            if (delivery.getAttemptCount() >= delivery.getMaxAttempts()) {
                delivery.setStatus(NotificationStatus.FAILED);
            } else {
                delivery.setStatus(NotificationStatus.PENDING);
            }

            attemptRepository.save(new DeliveryAttempt(delivery, attemptNumber, DeliveryAttemptResult.FAILURE, exception.getMessage()));
        }

        return deliveryRepository.save(delivery);
    }

    // Finds the sender that knows how to deliver this type (e.g. EMAIL).
    private NotificationSender findSender(NotificationDelivery delivery) {
        for (NotificationSender sender : senders) {
            if (sender.supports(delivery.getType())) {
                return sender;
            }
        }
        throw new IllegalStateException("No sender available for type: " + delivery.getType());
    }

    public List<EmailRecipient> parseRecipients(
            String addresses,
            EmailRecipientType type) {

        if (addresses == null || addresses.isBlank()) {
            return List.of();
        }

        List<EmailRecipient> recipients = new ArrayList<>();

        for (String address : addresses.split("[,;]")) {
            String trimmed = address.trim();

            if (!trimmed.isEmpty()) {
                recipients.add(new EmailRecipient(trimmed, type));
            }
        }

        return recipients;
    }

    public String findEmailSubjectByNotificationId(Long notificationId) {

        List<NotificationDelivery> deliveries = deliveryRepository.findByNotificationId(notificationId);

        for (NotificationDelivery delivery : deliveries) {
            if (delivery.getType() == NotificationType.EMAIL) {
                return emailDetailsRepository.findByDeliveryId(delivery.getId()).map(EmailDetails::getSubject).orElse("(No subject)");
            }
        }

        return "(No subject)";
    }

    public List<NotificationListItem> findAllNotificationItems() {

        List<NotificationListItem> items = new ArrayList<>();

        for (Notification notification : notificationRepository.findAll()) {
            items.add(new NotificationListItem(notification.getId(), findEmailSubjectByNotificationId(notification.getId()), notification.getCreatedAt()));
        }

        return items;
    }

    @Transactional
    public EmailDetailsDto findEmailDetailsByDeliveryId(Long deliveryId) {

        NotificationDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));

        if (delivery.getType() != NotificationType.EMAIL) {
            throw new IllegalArgumentException("This delivery is not an email.");
        }

        EmailDetails emailDetails = emailDetailsRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Email details not found for delivery: " + deliveryId));

        return new EmailDetailsDto(delivery.getId(), emailDetails.getSubject(), delivery.getNotification().getContent(),
                new ArrayList<>(emailDetails.getRecipients()));
    }

    @Transactional
    public NotificationDelivery sendEmailDeliveryForNotification(Long notificationId) {

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        NotificationDelivery delivery = deliveryRepository
                .findByNotificationId(notification.getId()).stream()
                .filter(d -> d.getType() == NotificationType.EMAIL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Email delivery not found for notification: " + notificationId));

        return sendDelivery(delivery.getId());
    }


    public List<NotificationListItem> findNotificationItems(List<Notification> notifications) {

        List<NotificationListItem> items = new ArrayList<>();

        for (Notification notification : notifications) {
            items.add(new NotificationListItem(
                    notification.getId(),
                    findEmailSubjectByNotificationId(notification.getId()),
                    notification.getCreatedAt()));
        }

        return items;
    }

    @Transactional
    public List<DeliveryDetailsDto> findDeliveryDetailsByNotificationId(
            Long notificationId) {

        findNotificationById(notificationId);

        return deliveryRepository.findByNotificationId(notificationId)
                .stream()
                .map(this::toDeliveryDetailsDto)
                .toList();
    }

    @Transactional
    public DeliveryDetailsDto findDeliveryDetailsById(Long deliveryId) {

        NotificationDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Delivery not found: " + deliveryId));

        return toDeliveryDetailsDto(delivery);
    }

    private DeliveryDetailsDto toDeliveryDetailsDto(NotificationDelivery delivery) {

        List<DeliveryAttempt> attempts =
                attemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(delivery.getId());

        List<DeliveryAttemptDto> attemptDtos = attempts.stream()
                .map(attempt -> new DeliveryAttemptDto(
                        attempt.getAttemptNumber(),
                        attempt.getAttemptedAt(),
                        attempt.getResult(),
                        attempt.getErrorMessage()))
                .toList();

        String lastErrorMessage = attempts.isEmpty()
                ? null
                : attempts.get(attempts.size() - 1).getErrorMessage();

        return new DeliveryDetailsDto(
                delivery.getId(),
                delivery.getType(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getMaxAttempts(),
                delivery.getSentAt(),
                lastErrorMessage,
                attemptDtos);
    }
}