package pt.uportalegre.estgd.ei.es.notificationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.MonitorItemDto;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.QueueSummaryDto;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.repository.NotificationDeliveryRepository;

import java.util.List;

@Service
public class MonitorService {

    private final NotificationDeliveryRepository deliveryRepository;

    public MonitorService(NotificationDeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public QueueSummaryDto findQueueSummary() {
        return new QueueSummaryDto(
                deliveryRepository.countByStatus(NotificationStatus.PENDING),
                deliveryRepository.countByStatus(NotificationStatus.PROCESSING),
                deliveryRepository.countByStatus(NotificationStatus.SENT),
                deliveryRepository.countByStatus(NotificationStatus.FAILED),
                deliveryRepository.countByStatusAndNotificationPriority(NotificationStatus.PENDING, NotificationPriority.HIGH),
                deliveryRepository.countByStatusAndNotificationPriority(NotificationStatus.PENDING, NotificationPriority.NORMAL),
                deliveryRepository.countByStatusAndNotificationPriority(NotificationStatus.PENDING, NotificationPriority.LOW),
                toItems(deliveryRepository.findByStatusOrderByLockedAtAsc(NotificationStatus.PROCESSING)),
                toItems(deliveryRepository.findTop10ByStatusOrderBySentAtDesc(NotificationStatus.SENT)));
    }

    private List<MonitorItemDto> toItems(List<NotificationDelivery> deliveries) {
        return deliveries.stream()
                .map(delivery -> new MonitorItemDto(
                        delivery.getId(),
                        delivery.getNotification().getId(),
                        delivery.getNotification().getPriority(),
                        delivery.getLockedBy(),
                        delivery.getLockedAt(),
                        delivery.getSentAt(),
                        delivery.getAttemptCount(),
                        delivery.getMaxAttempts()))
                .toList();
    }
}
