package pt.uportalegre.estgd.ei.es.notificationservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home() {
        return "redirect:/notifications";
    }
}
