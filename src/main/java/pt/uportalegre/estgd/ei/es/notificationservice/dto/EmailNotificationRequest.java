package pt.uportalegre.estgd.ei.es.notificationservice.dto;

import java.util.List;

public class EmailNotificationRequest {

    private List<String> toReceivers;
    private List<String> ccReceivers;
    private List<String> bccReceivers;
    private String subject;
    private String content;

    public List<String> getToReceivers() {
        return toReceivers;
    }

    public void setToReceivers(List<String> toReceivers) {
        this.toReceivers = toReceivers;
    }

    public List<String> getCcReceivers() {
        return ccReceivers;
    }

    public void setCcReceivers(List<String> ccReceivers) {
        this.ccReceivers = ccReceivers;
    }

    public List<String> getBccReceivers() {
        return bccReceivers;
    }

    public void setBccReceivers(List<String> bccReceivers) {
        this.bccReceivers = bccReceivers;
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
