package pt.uportalegre.estgd.ei.es.notificationservice.exception;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Substituir pelo URL do vosso canal #🚨-app-alertas
    private final String DISCORD_WEBHOOK = "https://discord.com/api/webhooks/1557003620359344279/QB57eQ56ix-PhEhI_YsB385X4-I0Tsgpou3UTr4U8GgsaanoVlHKXrbiZqeZMtNbFnfU";

    @ExceptionHandler(Exception.class)
    public void handleAllExceptions(Exception ex) {
        String mensagem = "⚠️ **ERRO NA API:**\n`" + ex.getMessage() + "`";

        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForEntity(DISCORD_WEBHOOK, Map.of("content", mensagem), String.class);
        } catch (Exception e) {
            // Ignorar falhas de envio
        }

        ex.printStackTrace();
    }
}