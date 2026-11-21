package pt.uportalegre.estgd.ei.es.notificationservice.simulation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Profile("simulation")
@RequestMapping("/simulation")
public class SimulationController {

    private final SimulatedEmailSender emailSender;

    public SimulationController(SimulatedEmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @GetMapping
    public String showSimulation(Model model) {
        model.addAttribute("failuresRemaining", emailSender.getFailuresLeft());
        model.addAttribute("alwaysFail", emailSender.isAlwaysFail());
        model.addAttribute("delayMs", emailSender.getDelayMs());
        model.addAttribute("sentCount", emailSender.getSentCount());

        return "simulation";
    }

    @PostMapping
    public String updateSimulation(@RequestParam(defaultValue = "0") int failures,
                                   @RequestParam(defaultValue = "false") boolean alwaysFail,
                                   @RequestParam(defaultValue = "0") long delayMs) {
        emailSender.configure(failures, alwaysFail, delayMs);

        return "redirect:/simulation";
    }
}
