package pt.uportalegre.estgd.ei.es.notificationservice.sender;

import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;

public interface NotificationSender {
    void send(NotificationDelivery delivery);
    boolean supports(NotificationType type);
}
