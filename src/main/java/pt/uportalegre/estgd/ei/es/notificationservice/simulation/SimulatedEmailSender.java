package pt.uportalegre.estgd.ei.es.notificationservice.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;
import pt.uportalegre.estgd.ei.es.notificationservice.model.NotificationDelivery;
import pt.uportalegre.estgd.ei.es.notificationservice.model.enums.NotificationType;
import pt.uportalegre.estgd.ei.es.notificationservice.sender.NotificationSender;

@Component
@Profile("simulation")
public class SimulatedEmailSender implements NotificationSender {

    private static final Logger logger = LoggerFactory.getLogger(SimulatedEmailSender.class);

    private int failuresLeft;
    private boolean alwaysFail;
    private long delayMs;
    private int sentCount;

    public SimulatedEmailSender(@Value("${notifications.simulation.failures:0}") int failures,
                                @Value("${notifications.simulation.always-fail:false}") boolean alwaysFail,
                                @Value("${notifications.simulation.delay-ms:0}") long delayMs) {
        configure(failures, alwaysFail, delayMs);
    }

    @Override
    public void send(NotificationDelivery delivery) {
        waitForDelay();

        if (consumeFailure()) {
            logger.warn("Simulated failure for delivery {}.", delivery.getId());
            throw new MailSendException("Simulated email failure");
        }

        registerSent();
        logger.info("Simulated email sent for delivery {}.", delivery.getId());
    }

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.EMAIL;
    }

    public synchronized void configure(int failures, boolean alwaysFail, long delayMs) {
        if (failures < 0) {
            throw new IllegalArgumentException("Failures cannot be negative.");
        }

        if (delayMs < 0) {
            throw new IllegalArgumentException("Delay cannot be negative.");
        }

        this.failuresLeft = failures;
        this.alwaysFail = alwaysFail;
        this.delayMs = delayMs;
    }

    public synchronized void reset() {
        this.failuresLeft = 0;
        this.alwaysFail = false;
        this.delayMs = 0;
        this.sentCount = 0;
    }

    public synchronized int getFailuresLeft() {
        return failuresLeft;
    }

    public synchronized boolean isAlwaysFail() {
        return alwaysFail;
    }

    public synchronized long getDelayMs() {
        return delayMs;
    }

    public synchronized int getSentCount() {
        return sentCount;
    }

    private void waitForDelay() {
        long delay = getDelayMs();

        if (delay <= 0) {
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MailSendException("Simulated email interrupted");
        }
    }

    private synchronized boolean consumeFailure() {
        if (alwaysFail) {
            return true;
        }

        if (failuresLeft > 0) {
            failuresLeft--;
            return true;
        }

        return false;
    }

    private synchronized void registerSent() {
        sentCount++;
    }
}
