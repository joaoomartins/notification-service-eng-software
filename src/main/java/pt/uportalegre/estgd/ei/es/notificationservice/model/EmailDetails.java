package pt.uportalegre.estgd.ei.es.notificationservice.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "email_details")
public class EmailDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false, unique = true)
    private NotificationDelivery delivery;

    @Column(nullable = false)
    private String subject;

    @ElementCollection //map basic collections (strings,enums,etc) and @Embbeddable
    @CollectionTable(name = "email_recipients",joinColumns = @JoinColumn(name = "email_details_id"))
    @AttributeOverrides({
            @AttributeOverride(name="emailAddress", column = @Column(name = "email_address", nullable = false)),
            @AttributeOverride(name="recipientType", column = @Column(name = "recipient_type",nullable = false))
    })
    private List<EmailRecipient> recipients = new ArrayList<>();

    protected EmailDetails() {

    }

    public EmailDetails(NotificationDelivery delivery, String subject) {
        this.delivery = delivery;
        this.subject = subject;
    }

    public Long getId() {
        return id;
    }

    public NotificationDelivery getDelivery() {
        return delivery;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public List<EmailRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<EmailRecipient> recipients) {
        this.recipients = recipients;
    }
}
