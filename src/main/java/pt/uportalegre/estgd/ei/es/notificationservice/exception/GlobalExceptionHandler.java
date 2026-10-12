package pt.uportalegre.estgd.ei.es.notificationservice.exception;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @Value("${discord.webhook.url}")
    private String webhookUrl;

    /*@ExceptionHandler(NoResourceFoundException.class)
    public void handleNotFound(NoResourceFoundException ex) {
        System.out.println("Alguém tentou aceder a uma rota inexistente: " + ex.getMessage());
    }*/

    @ExceptionHandler(Exception.class)
    public void handleAllExceptions(Exception ex) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            Map<String, Object> embed = Map.of(
                    "title", "🚨 Erro Crítico na API",
                    "description", "```\n" + ex.getMessage() + "\n```",
                    "color", 1671168
            );

            Map<String, Object> payload = Map.of(
                    "content", "Urgente -> ver os logs rápido! @here",
                    "embeds", List.of(embed)
            );

            restTemplate.postForEntity(webhookUrl, payload, String.class);

        } catch (Exception e) {
            System.err.println("Falha ao enviar o alerta para o Discord!");
        }

        ex.printStackTrace();
    }
}