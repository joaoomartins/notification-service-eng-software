package pt.uportalegre.estgd.ei.es.notificationservice.sender;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import pt.uportalegre.estgd.ei.es.notificationservice.model.EmailNotification;
import pt.uportalegre.estgd.ei.es.notificationservice.model.Notification;

@Component
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;

    public EmailNotificationSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(Notification notification) {

        if(!(notification instanceof EmailNotification)) { //verify if is a notification instance
            throw new IllegalArgumentException(("Notification type not supported by EmailNotificationSender"));
        }

        EmailNotification email = (EmailNotification) notification;
        SimpleMailMessage mailMessage = new SimpleMailMessage();

        mailMessage.setTo(email.getToReceivers().toArray(new String[0])); //convert a list to array string

        if (email.getCcReceivers() != null && !email.getCcReceivers().isEmpty()) { //not null && not empty
            mailMessage.setCc(email.getCcReceivers().toArray(new String[0]));
        }

        if (email.getBccReceivers() != null && !email.getBccReceivers().isEmpty()) {
            mailMessage.setBcc(email.getBccReceivers().toArray(new String[0]));
        }

        mailMessage.setSubject(email.getSubject());
        mailMessage.setText(email.getContent());

        mailSender.send(mailMessage);
    }
}