package pt.uportalegre.estgd.ei.es.notificationservice.service;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.EmailDetailsDto;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.NotificationListItem;
import pt.uportalegre.estgd.ei.es.notificationservice.model.*;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.EmailRecipientType;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.EmailDetailsRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.sender.NotificationSender;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDetailsRepository emailDetailsRepository;
    private final List<NotificationSender> senders;

    public NotificationService(NotificationRepository notificationRepository, NotificationDeliveryRepository deliveryRepository, EmailDetailsRepository emailDetailsRepository, List<NotificationSender> senders) {
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.emailDetailsRepository = emailDetailsRepository;
        this.senders = senders;
    }

    public List<Notification> findAllNotifications() {
        return notificationRepository.findAll();
    }

    public Page<Notification> findPaginated(Pageable pageable) {
        return notificationRepository.findAll(pageable);
    }

    public Notification findNotificationById(Long id) {
        return notificationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
    }

    public List<NotificationDelivery> findDeliveriesByNotificationId(Long notificationId) {
        return deliveryRepository.findByNotificationId(notificationId);
    }

    @Transactional
    public Notification createEmailNotification(String content, String subject, List<EmailRecipient> recipients) {

        //cant create a notification without content
        Notification notification = new Notification(content);
        notification = notificationRepository.save(notification);

        //associate a type to this notification
        NotificationDelivery delivery = new NotificationDelivery(notification, NotificationType.EMAIL);
        delivery = deliveryRepository.save(delivery);

        //
        EmailDetails emailDetails = new EmailDetails(delivery, subject);
        emailDetails.getRecipients().addAll(recipients);

        emailDetailsRepository.save(emailDetails);

        return notification;
    }

    @Transactional
    public Notification createEmailNotification(
            String content,
            String subject,
            String to,
            String cc,
            String bcc) {

        List<EmailRecipient> recipients = new java.util.ArrayList<>();

        recipients.addAll(parseRecipients(to, EmailRecipientType.TO));
        recipients.addAll(parseRecipients(cc, EmailRecipientType.CC));
        recipients.addAll(parseRecipients(bcc, EmailRecipientType.BCC));

        if (recipients.stream().noneMatch(
                recipient -> recipient.getRecipientType() == EmailRecipientType.TO)) {
            throw new IllegalArgumentException(
                    "At least one TO recipient is required.");
        }

        return createEmailNotification(content, subject, recipients);
    }

    @Transactional
    public NotificationDelivery sendDelivery(Long deliveryId) {

        NotificationDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));

        NotificationSender sender = senders.stream()
                .filter(s -> s.supports(delivery.getType())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No sender available for type: " + delivery.getType()));

        try {
            sender.send(delivery);

            delivery.setStatus(NotificationStatus.SENT);
            delivery.setSentAt(LocalDateTime.now());
        } catch (MailException exception) {
            delivery.setStatus(NotificationStatus.FAILED);
            delivery.setSentAt(null);
        }

        return deliveryRepository.save(delivery);
    }

    public List<EmailRecipient> parseRecipients(String addresses, EmailRecipientType type) {
        if (addresses == null || addresses.isBlank()) {
            return List.of();
        }

        List<EmailRecipient> list = new ArrayList<>();

        for (String address : addresses.split("[,;]")) {
            String trimmed = address.trim();
            if (!trimmed.isEmpty()) {
                list.add(new EmailRecipient(trimmed, type));
            }
        }
        return list;
    }


    public String findEmailSubjectByNotificationId(Long notificationId) {
        List<NotificationDelivery> deliveries = deliveryRepository.findByNotificationId(notificationId);

        for (NotificationDelivery delivery : deliveries) {
            if (delivery.getType() == NotificationType.EMAIL) {
                return emailDetailsRepository.findByDeliveryId(delivery.getId())
                        .map(EmailDetails::getSubject)
                        .orElse("(No subject)");
            }
        }

        return "(No subject)";
    }


    public List<NotificationListItem> findAllNotificationItems() {
        List<NotificationListItem> items = new ArrayList<>();
        for (Notification n : notificationRepository.findAll()) {
            items.add(new NotificationListItem(
                    n.getId(),
                    findEmailSubjectByNotificationId(n.getId()),
                    n.getCreatedAt()
            ));
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

        EmailDetails emailDetails = emailDetailsRepository
                .findByDeliveryId(deliveryId).orElseThrow(() ->
                        new IllegalArgumentException("Email details not found for delivery: " + deliveryId));

        return new EmailDetailsDto(delivery.getId(), emailDetails.getSubject(), delivery.getNotification().getContent(),
                new ArrayList<>(emailDetails.getRecipients())
        );
    }

    @Transactional
    public NotificationDelivery sendEmailDeliveryForNotification(Long notificationId) {

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Notification not found: " + notificationId));

        NotificationDelivery delivery = deliveryRepository
                .findByNotificationId(notification.getId())
                .stream()
                .filter(d -> d.getType() == NotificationType.EMAIL)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Email delivery not found for notification: " + notificationId));

        return sendDelivery(delivery.getId());
    }
}