package com.expiryguard.service;

import com.expiryguard.entity.Secret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for sending webhook notifications to Slack, Discord, or generic
 * webhooks.
 */
@Service
@Slf4j
public class WebhookService {

    @Value("${expiryguard.webhook.slack.url:}")
    private String slackWebhookUrl;

    @Value("${expiryguard.webhook.discord.url:}")
    private String discordWebhookUrl;

    @Value("${expiryguard.webhook.generic.url:}")
    private String genericWebhookUrl;

    @Value("${expiryguard.webhook.enabled:true}")
    private boolean webhookEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Check if any webhook is configured.
     */
    public boolean isWebhookConfigured() {
        return webhookEnabled && (!slackWebhookUrl.isBlank() ||
                !discordWebhookUrl.isBlank() ||
                !genericWebhookUrl.isBlank());
    }

    /**
     * Send notification to all configured webhooks.
     */
    public void sendExpiryNotification(Secret secret, int threshold) {
        if (!webhookEnabled) {
            log.debug("Webhooks disabled, skipping notification");
            return;
        }

        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), secret.getExpiryDate());
        String urgency = getUrgencyLabel(threshold);
        String emoji = getUrgencyEmoji(threshold);

        // Send to Slack
        if (!slackWebhookUrl.isBlank()) {
            sendSlackNotification(secret, daysRemaining, urgency, emoji);
        }

        // Send to Discord
        if (!discordWebhookUrl.isBlank()) {
            sendDiscordNotification(secret, daysRemaining, urgency, emoji);
        }

        // Send to generic webhook
        if (!genericWebhookUrl.isBlank()) {
            sendGenericNotification(secret, daysRemaining, threshold, urgency);
        }
    }

    /**
     * Send batch notification summary to webhooks.
     */
    public void sendBatchSummary(int totalSecrets, int notificationsSent, List<String> urgentSecrets) {
        if (!webhookEnabled || !isWebhookConfigured()) {
            return;
        }

        String summaryMessage = String.format(
                "📊 *ExpiryGuard Daily Summary*\n" +
                        "• Secrets monitored: %d\n" +
                        "• Notifications sent: %d\n" +
                        "%s",
                totalSecrets,
                notificationsSent,
                urgentSecrets.isEmpty() ? "• No urgent secrets today!"
                        : "• ⚠️ Urgent: " + String.join(", ", urgentSecrets));

        if (!slackWebhookUrl.isBlank()) {
            sendSlackMessage(summaryMessage);
        }

        if (!discordWebhookUrl.isBlank()) {
            sendDiscordMessage(summaryMessage.replace("*", "**"));
        }
    }

    private void sendSlackNotification(Secret secret, long daysRemaining, String urgency, String emoji) {
        String message = String.format(
                "%s *[%s]* Secret *%s* expires in *%d days* (%s)\n" +
                        "Owner: %s",
                emoji,
                urgency,
                secret.getName(),
                daysRemaining,
                secret.getExpiryDate().toString(),
                secret.getUser().getEmail());

        sendSlackMessage(message);
    }

    private void sendSlackMessage(String message) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            payload.put("mrkdwn", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(slackWebhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Slack notification sent successfully");
            } else {
                log.warn("Slack notification failed with status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send Slack notification: {}", e.getMessage());
        }
    }

    private void sendDiscordNotification(Secret secret, long daysRemaining, String urgency, String emoji) {
        String message = String.format(
                "%s **[%s]** Secret **%s** expires in **%d days** (%s)\n" +
                        "Owner: %s",
                emoji,
                urgency,
                secret.getName(),
                daysRemaining,
                secret.getExpiryDate().toString(),
                secret.getUser().getEmail());

        sendDiscordMessage(message);
    }

    private void sendDiscordMessage(String message) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("content", message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(discordWebhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Discord notification sent successfully");
            } else {
                log.warn("Discord notification failed with status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send Discord notification: {}", e.getMessage());
        }
    }

    private void sendGenericNotification(Secret secret, long daysRemaining, int threshold, String urgency) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "secret_expiry_warning");
            payload.put("secret_name", secret.getName());
            payload.put("expiry_date", secret.getExpiryDate().toString());
            payload.put("days_remaining", daysRemaining);
            payload.put("threshold", threshold);
            payload.put("urgency", urgency);
            payload.put("owner_email", secret.getUser().getEmail());
            payload.put("timestamp", java.time.Instant.now().toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(genericWebhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Generic webhook notification sent successfully");
            } else {
                log.warn("Generic webhook failed with status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send generic webhook: {}", e.getMessage());
        }
    }

    private String getUrgencyLabel(int threshold) {
        return switch (threshold) {
            case 3 -> "URGENT";
            case 7 -> "WARNING";
            case 30 -> "NOTICE";
            default -> "INFO";
        };
    }

    private String getUrgencyEmoji(int threshold) {
        return switch (threshold) {
            case 3 -> "🚨";
            case 7 -> "⚠️";
            case 30 -> "📅";
            default -> "ℹ️";
        };
    }
}
