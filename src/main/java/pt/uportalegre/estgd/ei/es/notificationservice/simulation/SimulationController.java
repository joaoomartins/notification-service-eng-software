package pt.uportalegre.estgd.ei.es.notificationservice.simulation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Page /simulation, used during the demo to change the failures while the app is running.
 * Only exists with the "simulation" profile.
 */
@Controller
@Profile("simulation")
@RequestMapping("/simulation")
public class SimulationController {

    private final SimulatedEmailSender emailSender;

    public SimulationController(SimulatedEmailSender emailSender) {
        this.emailSender = emailSender;
    }

    // Shows the current simulation settings.
    @GetMapping
    public String showSimulation(Model model) {
        model.addAttribute("failuresRemaining", emailSender.getFailuresLeft());
        model.addAttribute("alwaysFail", emailSender.isAlwaysFail());
        model.addAttribute("sentCount", emailSender.getSentCount());

        return "simulation";
    }

    // Saves the new settings from the form.
    @PostMapping
    public String updateSimulation(@RequestParam(defaultValue = "0") int failures,
                                   @RequestParam(defaultValue = "false") boolean alwaysFail) {
        emailSender.configure(failures, alwaysFail);

        return "redirect:/simulation";
    }
}
