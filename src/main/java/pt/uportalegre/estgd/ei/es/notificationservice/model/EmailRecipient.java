package pt.uportalegre.estgd.ei.es.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.EmailRecipientType;

@Embeddable //this attributes can be embedded directly within another entity
public class EmailRecipient {

    @Column(name = "email_address", nullable = false)
    private String emailAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false)
    private EmailRecipientType recipientType;

    public EmailRecipient() {

    }

    public EmailRecipient(String emailAddress, EmailRecipientType recipientType) {
        this.emailAddress = emailAddress;
        this.recipientType = recipientType;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public EmailRecipientType getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(EmailRecipientType recipientType) {
        this.recipientType = recipientType;
    }
}