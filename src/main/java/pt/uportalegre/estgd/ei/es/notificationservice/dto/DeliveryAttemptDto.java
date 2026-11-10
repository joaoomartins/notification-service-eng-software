package pt.uportalegre.estgd.ei.es.notificationservice.dto;

import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.DeliveryAttemptResult;

import java.time.LocalDateTime;

public class DeliveryAttemptDto {
    private final int attemptNumber;
    private final LocalDateTime attemptedAt;
    private final DeliveryAttemptResult result;
    private final String errorMessage;

    public DeliveryAttemptDto(
            int attemptNumber,
            LocalDateTime attemptedAt,
            DeliveryAttemptResult result,
            String errorMessage) {
        this.attemptNumber = attemptNumber;
        this.attemptedAt = attemptedAt;
        this.result = result;
        this.errorMessage = errorMessage;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public DeliveryAttemptResult getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
