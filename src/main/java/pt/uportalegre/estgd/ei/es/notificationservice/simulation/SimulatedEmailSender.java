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

/**
 * Fake email sender, only active with the "simulation" profile.
 *
 * It implements the same NotificationSender interface as the real EmailNotificationSender,
 * so NotificationService does not know (and does not care) which one it is using.
 * No real email is sent. Instead, we can choose how many attempts should fail,
 * to test retries and the attempt limit.
 */
@Component
@Profile("simulation")
public class SimulatedEmailSender implements NotificationSender {

    private static final Logger logger = LoggerFactory.getLogger(SimulatedEmailSender.class);

    // How many of the next attempts will fail.
    private int failuresLeft;

    // If true, every attempt fails.
    private boolean alwaysFail;

    // How many "emails" were sent successfully (useful in tests).
    private int sentCount;

    // Initial values come from application.properties.
    public SimulatedEmailSender(@Value("${notifications.simulation.failures:0}") int failures,
                                @Value("${notifications.simulation.always-fail:false}") boolean alwaysFail) {
        configure(failures, alwaysFail);
    }

    // The methods are "synchronized" because two threads use this object:
    // the BackgroundWorker (sends) and the web page /simulation (changes the settings).
    @Override
    public synchronized void send(NotificationDelivery delivery) {
        if (alwaysFail) {
            logger.warn("Simulated failure for delivery {}.", delivery.getId());
            throw new MailSendException("Simulated email failure");
        }

        if (failuresLeft > 0) {
            failuresLeft--;
            logger.warn("Simulated failure for delivery {}. Failures left: {}.", delivery.getId(), failuresLeft);
            throw new MailSendException("Simulated email failure");
        }

        sentCount++;
        logger.info("Simulated email sent for delivery {}.", delivery.getId());
    }

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.EMAIL;
    }

    public synchronized void configure(int failures, boolean alwaysFail) {
        if (failures < 0) {
            throw new IllegalArgumentException("Failures cannot be negative.");
        }
        this.failuresLeft = failures;
        this.alwaysFail = alwaysFail;
    }

    // Back to the default: no failures and nothing sent.
    public synchronized void reset() {
        this.failuresLeft = 0;
        this.alwaysFail = false;
        this.sentCount = 0;
    }

    public synchronized int getFailuresLeft() {
        return failuresLeft;
    }

    public synchronized boolean isAlwaysFail() {
        return alwaysFail;
    }

    public synchronized int getSentCount() {
        return sentCount;
    }
}
