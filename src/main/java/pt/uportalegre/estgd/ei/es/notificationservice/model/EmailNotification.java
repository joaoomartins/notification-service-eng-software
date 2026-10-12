package pt.uportalegre.estgd.ei.es.notificationservice.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "email_notifications")
public class EmailNotification extends Notification {

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "email_destinations_to", joinColumns = @JoinColumn(name = "email_notification_id"))
    @Column(name = "email_address")
    private List<String> toReceiversList = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "email_destinations_cc", joinColumns = @JoinColumn(name = "email_notification_id"))
    @Column(name = "email_address")
    private List<String> ccReceiversList = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "email_destinations_bcc", joinColumns = @JoinColumn(name = "email_notification_id"))
    @Column(name = "email_address")
    private List<String> bccReceiversList = new ArrayList<>();

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String content;

    public List<String> getToReceivers() {
        return toReceiversList;
    }

    public void setToReceivers(List<String> toReceivers) {
        this.toReceiversList = toReceivers;
    }

    public List<String> getCcReceivers() {
        return ccReceiversList;
    }

    public void setCcReceivers(List<String> ccReceivers) {
        this.ccReceiversList = ccReceivers;
    }

    public List<String> getBccReceivers() {
        return bccReceiversList;
    }

    public void setBccReceivers(List<String> bccReceivers) {
        this.bccReceiversList = bccReceivers;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
