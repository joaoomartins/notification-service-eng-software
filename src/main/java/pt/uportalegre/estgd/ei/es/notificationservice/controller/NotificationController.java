package pt.uportalegre.estgd.ei.es.notificationservice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pt.uportalegre.estgd.ei.es.notificationservice.dto.EmailNotificationRequest;
import pt.uportalegre.estgd.ei.es.notificationservice.model.EmailNotification;
import pt.uportalegre.estgd.ei.es.notificationservice.model.Notification;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationStatus;
import pt.uportalegre.estgd.ei.es.notificationservice.service.NotificationService;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    //accesing /notification user gonna saw page 0 and 5 items by default
    @GetMapping
    public String listNotifications(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size, Model model) {

        //decrescent order (order by createAt)
        Page<Notification> notificationPage = notificationService.findPaginated(PageRequest.of(page,size, Sort.Direction.DESC, "createdAt"));

        model.addAttribute("notificationPage", notificationPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);

        return "notifications";
    }

    @GetMapping("/new")
    public String createNotification(Model model) {
        model.addAttribute("request", new EmailNotificationRequest());
        return "notifications-form";
    }

    @PostMapping("/save")
    public String saveNotification(@ModelAttribute("request") EmailNotificationRequest request,
                                   RedirectAttributes redirectAttributes) {
        notificationService.save(request);
        redirectAttributes.addFlashAttribute("successMessage", "Notification saved successfully.");
        return "redirect:/notifications";
    }

    @PostMapping("/send")
    public String sendNotification(@ModelAttribute("request") EmailNotificationRequest request,
                                   RedirectAttributes redirectAttributes) {
        notificationService.saveAndSent(request);
        redirectAttributes.addFlashAttribute("successMessage", "Notification created and sent.");
        return "redirect:/notifications";
    }

    @GetMapping("/view/{id}")
    public String viewNotification(@PathVariable Long id, Model model) {
        Notification notification = notificationService.findById(id);

        model.addAttribute("notification", notification);

        //ask if 'notification' is an object of 'EmailNotification'
        //if true convert and create a emailNotification var and add to model with "emailNotification" name
        if (notification instanceof EmailNotification emailNotification) {
            model.addAttribute("emailNotification", emailNotification);
        }

        return "notification-details";
    }

    @PostMapping("/{id}/send")
    public String sendExisting(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Notification notification = notificationService.sendExisting(id);

        if (notification.getStatus() == NotificationStatus.SENT) {
            redirectAttributes.addFlashAttribute("successMessage", "Notification sent successfully.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while trying to send the notification.");
        }

        return "redirect:/notifications";
    }
}