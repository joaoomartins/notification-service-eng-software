package pt.uportalegre.estgd.ei.es.notificationservice.sender;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import pt.uportalegre.estgd.ei.es.notificationservice.model.EmailNotification;
import pt.uportalegre.estgd.ei.es.notificationservice.model.Notification;

import java.sql.SQLOutput;

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

        if(email.getToReceivers() != null && !email.getToReceivers().isEmpty()) {
            System.out.println("Erro no to receivers");
            mailMessage.setTo(email.getToReceivers().toArray(new String[0])); //convert a list to array string
        }

        if (email.getCcReceivers() != null && !email.getCcReceivers().isEmpty()) { //not null && not empty
            System.out.println("Erro no cc receivers");
            mailMessage.setCc(email.getCcReceivers().toArray(new String[0]));
        }

        if (email.getBccReceivers() != null && !email.getBccReceivers().isEmpty()) {
            mailMessage.setBcc(email.getBccReceivers().toArray(new String[0]));
        }

        if (email.getSubject() != null && !email.getSubject().isEmpty()) {
            mailMessage.setSubject(email.getSubject());
        }

        if (email.getContent() != null && !email.getContent().isEmpty()) {
            mailMessage.setText(email.getContent());
        }

        mailSender.send(mailMessage);
    }
}