package pt.uportalegre.estgd.ei.es.notificationservice.dto;

import pt.uportalegre.estgd.ei.es.notificationservice.model.EmailRecipient;

import java.util.List;

public class EmailDetailsDto {
    private final Long deliveryId;
    private final String subject;
    private final String content;
    private final List<EmailRecipient> recipients;

    public EmailDetailsDto(Long deliveryId, String subject, String content, List<EmailRecipient> recipients) {
        this.deliveryId = deliveryId;
        this.subject = subject;
        this.content = content;
        this.recipients = recipients;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public List<EmailRecipient> getRecipients() {
        return recipients;
    }
}
