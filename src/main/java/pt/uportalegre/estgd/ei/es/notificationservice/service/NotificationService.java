package pt.uportalegre.estgd.ei.es.notificationservice.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.EmailNotificationRequest;
import pt.uportalegre.estgd.ei.es.notificationservice.model.EmailNotification;
import pt.uportalegre.estgd.ei.es.notificationservice.model.Notification;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationType;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationRepository;
import pt.uportalegre.estgd.ei.es.notificationservice.sender.NotificationSender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;

    public NotificationService(NotificationRepository notificationRepository, NotificationSender notificationSender) {
        this.notificationRepository = notificationRepository;
        this.notificationSender = notificationSender;
    }

    @Transactional
    public EmailNotification save(EmailNotificationRequest request) {
        EmailNotification notification = createEmailNotification(request);
        return notificationRepository.save(notification);
    }

    @Transactional
    public EmailNotification saveAndSent(EmailNotificationRequest request) {
        EmailNotification notification = createEmailNotification(request);

        attemptSend(notification);

        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification sendExisting(Long id) {
        Notification notification = findById(id);

        if(!(notification instanceof EmailNotification)) {
            throw new IllegalArgumentException("Notification type not supported");
        }

        if(notification.getStatus() == NotificationStatus.SENT){
            throw  new IllegalArgumentException("Notification already sent");
        }

        attemptSend(notification);

        return notificationRepository.save(notification);
    }

    public Notification findById(Long id){
        return notificationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException(
                "Notification not found: " + id
                ));
    }
    
    public List<Notification> findAll() {
        return notificationRepository.findAll();
    }

    //aux func to sent and set status
    private void attemptSend(Notification notification) {
        try {
            notificationSender.send(notification);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } catch (RuntimeException exception) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setSentAt(null);
        }
    }

    public Page<Notification> findPaginated(Pageable pageable) {
        return notificationRepository.findAll(pageable);
    }

    private EmailNotification createEmailNotification(EmailNotificationRequest request){

        EmailNotification emailNotification = new EmailNotification();

        emailNotification.setToReceivers(request.getToReceivers());
        emailNotification.setCcReceivers(request.getCcReceivers());
        emailNotification.setBccReceivers(request.getBccReceivers());
        emailNotification.setSubject(request.getSubject());
        emailNotification.setContent(request.getContent());

        emailNotification.setType(NotificationType.EMAIL);
        emailNotification.setStatus(NotificationStatus.PENDING);
        emailNotification.setCreatedAt(LocalDateTime.now());

        return emailNotification;
    }
}