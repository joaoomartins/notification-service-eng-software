
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

import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDetailsRepository emailDetailsRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final DeliveryProcessor deliveryProcessor;
    private final int maxAttempts;

    public NotificationService(NotificationRepository notificationRepository, NotificationDeliveryRepository deliveryRepository, EmailDetailsRepository emailDetailsRepository,
            DeliveryAttemptRepository attemptRepository, DeliveryProcessor deliveryProcessor,
            @Value("${notifications.delivery.max-attempts:3}") int maxAttempts) {

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("notifications.delivery.max-attempts must be at least 1.");
        }

        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.emailDetailsRepository = emailDetailsRepository;
        this.attemptRepository = attemptRepository;
        this.deliveryProcessor = deliveryProcessor;
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

        return createEmailNotification(content, subject, recipients, priority);
    }

    public NotificationDelivery sendDelivery(Long deliveryId) {

        deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));

        String processorId = deliveryProcessor.currentProcessorId();

        if (deliveryProcessor.claim(deliveryId, processorId)) {
            deliveryProcessor.process(deliveryId, processorId);
        }

        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));
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
