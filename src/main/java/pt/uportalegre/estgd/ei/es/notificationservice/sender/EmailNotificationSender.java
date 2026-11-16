package pt.uportalegre.estgd.ei.es.notificationservice.sender;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import pt.uportalegre.estgd.ei.es.notificationservice.model.*;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.EmailRecipientType;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.EmailDetailsRepository;

import java.util.List;

// Real email sender (Gmail SMTP).
// It is NOT used with the "simulation" profile: SimulatedEmailSender replaces it.
@Component
@Profile("!simulation")
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final EmailDetailsRepository emailDetailsRepository;

    public EmailNotificationSender(JavaMailSender mailSender, EmailDetailsRepository emailDetailsRepository) {
        this.mailSender = mailSender;
        this.emailDetailsRepository = emailDetailsRepository;
    }

    @Override
    public void send(NotificationDelivery delivery) {

        //find a notification details
        EmailDetails emailDetails = emailDetailsRepository.findByDeliveryId(delivery.getId()).orElseThrow(() ->
                new IllegalStateException("Email details not found for delivery " + delivery.getId()));

        List<EmailRecipient> recipients = emailDetails.getRecipients();

        String[] to = filterRecipients(recipients, EmailRecipientType.TO);
        String[] cc = filterRecipients(recipients, EmailRecipientType.CC);
        String[] bcc = filterRecipients(recipients, EmailRecipientType.BCC);

        SimpleMailMessage message = new SimpleMailMessage();
        if (to.length > 0) message.setTo(to);
        if (cc.length > 0) message.setCc(cc);
        if (bcc.length > 0) message.setBcc(bcc);

        message.setSubject(emailDetails.getSubject());
        message.setText(delivery.getNotification().getContent());

        mailSender.send(message);
    }

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.EMAIL;
    }

    private String[] filterRecipients(List<EmailRecipient> recipients, EmailRecipientType type) {
        return recipients.stream().filter(r -> r.getRecipientType() == type)
                .flatMap(r -> java.util.Arrays.stream(r.getEmailAddress().split("[,;]")))
                .map(String::trim).filter(address -> !address.isEmpty()).toArray(String[]::new);
    }
}
