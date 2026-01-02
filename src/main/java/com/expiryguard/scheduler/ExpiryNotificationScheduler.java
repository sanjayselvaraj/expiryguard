package com.expiryguard.scheduler;

import com.expiryguard.entity.Secret;
import com.expiryguard.service.EmailService;
import com.expiryguard.service.SecretService;
import com.expiryguard.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryNotificationScheduler {
    private final SecretService secretService;
    private final EmailService emailService;
    private final WebhookService webhookService;

    @Value("${expiryguard.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Scheduled(cron = "${expiryguard.scheduler.cron:0 0 9 * * *}", zone = "${expiryguard.scheduler.timezone:UTC}")
    @Transactional
    public void sendExpiryNotifications() {
        if (!schedulerEnabled) {
            log.info("ExpiryGuard: Scheduler disabled for this profile");
            return;
        }

        log.info("ExpiryGuard: Starting notification job at {}", LocalDateTime.now());

        // Get all secrets expiring within 30 days
        List<Secret> secrets = secretService.getSecretsExpiringWithin(30);
        log.info("Found {} secrets expiring within 30 days", secrets.size());

        int notificationsSent = 0;
        List<String> urgentSecrets = new ArrayList<>();

        for (Secret secret : secrets) {
            long daysRemaining = secretService.getDaysRemaining(secret);
            int threshold = secretService.getNotificationThreshold(secret);

            if (threshold > 0) {
                String urgency = secretService.getUrgencyLabel(threshold);
                log.info("Secret '{}' expires in {} days - sending {} notification ({}-day threshold)",
                        secret.getName(), daysRemaining, urgency, threshold);

                try {
                    // Send email notification
                    emailService.sendExpiryNotification(secret);

                    // Send webhook notification (Slack/Discord)
                    webhookService.sendExpiryNotification(secret, threshold);

                    // Mark as notified
                    secretService.markAsNotified(secret, threshold);
                    notificationsSent++;

                    // Track urgent secrets for summary
                    if (threshold == 3) {
                        urgentSecrets.add(secret.getName());
                    }

                    log.info("✓ {} notification sent for: {}", urgency, secret.getName());
                } catch (Exception e) {
                    log.error("✗ Failed to send notification for secret: {}", secret.getName(), e);
                }
            } else {
                log.debug("Secret '{}' expires in {} days - already notified",
                        secret.getName(), daysRemaining);
            }
        }

        // Send daily summary to webhooks
        if (webhookService.isWebhookConfigured()) {
            webhookService.sendBatchSummary(secrets.size(), notificationsSent, urgentSecrets);
        }

        log.info("ExpiryGuard: Notification job completed. Sent {} notifications", notificationsSent);
    }
}