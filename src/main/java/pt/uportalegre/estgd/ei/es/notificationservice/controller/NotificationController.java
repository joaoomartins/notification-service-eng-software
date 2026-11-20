package pt.uportalegre.estgd.ei.es.notificationservice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import pt.uportalegre.estgd.ei.es.notificationservice.dto.DeliveryDetailsDto;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.EmailDetailsDto;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.EmailNotificationRequest;
import pt.uportalegre.estgd.ei.es.notificationservice.model.Notification;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationPriority;
import pt.uportalegre.estgd.ei.es.notificationservice.service.NotificationService;

@Controller
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public String listNotifications(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size, Model model) {

        Page<Notification> notificationPage = notificationService.findPaginated(PageRequest.of(page, size, Sort.Direction.DESC, "createdAt"));

        model.addAttribute("notifications", notificationService.findNotificationItems(notificationPage.getContent()));

        model.addAttribute("notificationPage", notificationPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);

        return "notifications";
    }

    @GetMapping("/notifications/new")
    public String showCreateForm(Model model) {
        model.addAttribute("emailNotificationRequest", new EmailNotificationRequest());
        model.addAttribute("priorities", NotificationPriority.values());

        return "notifications-form";
    }

    @PostMapping("/notifications")
    public String createNotification(@RequestParam String content, @RequestParam String subject, @RequestParam String to,
                                     @RequestParam(required = false, defaultValue = "") String cc, @RequestParam(required = false, defaultValue = "") String bcc,
                                     @RequestParam(defaultValue = "NORMAL") NotificationPriority priority) {

        Notification notification = notificationService.createEmailNotificationFromStrings(content, subject, to, cc, bcc, priority);

        return "redirect:/notifications/" + notification.getId();
    }

    @GetMapping("/notifications/{id}")
    public String viewNotification(@PathVariable Long id, Model model) {

        model.addAttribute("notification", notificationService.findNotificationById(id));

        model.addAttribute("deliveries", notificationService.findDeliveryDetailsByNotificationId(id));

        return "notification-details";
    }

    @GetMapping("/deliveries/{deliveryId}/details")
    @ResponseBody
    public EmailDetailsDto getDeliveryDetails(@PathVariable Long deliveryId) {

        return notificationService.findEmailDetailsByDeliveryId(deliveryId);
    }

    @GetMapping("/deliveries/{deliveryId}/processing-details")
    @ResponseBody
    public DeliveryDetailsDto getProcessingDetails(
            @PathVariable Long deliveryId) {

        return notificationService.findDeliveryDetailsById(deliveryId);
    }
}