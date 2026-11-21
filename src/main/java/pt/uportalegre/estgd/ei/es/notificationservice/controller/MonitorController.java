package pt.uportalegre.estgd.ei.es.notificationservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import pt.uportalegre.estgd.ei.es.notificationservice.service.MonitorService;

@Controller
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/monitor")
    public String showMonitor(Model model) {
        model.addAttribute("summary", monitorService.findQueueSummary());

        return "monitor";
    }
}
