package pt.uportalegre.estgd.ei.es.notificationservice.sender;

import pt.uportalegre.estgd.ei.es.notificationservice.model.Notification;

public interface NotificationSender {
    void send(Notification notification);
}
