package pt.uportalegre.estgd.ei.es.notificationservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestDiscordController {

    @GetMapping("/testar-erro")
    public String forcarErro() {
        throw new RuntimeException("A base de dados explodiu! (Isto é só um teste do Discord)");
    }
}