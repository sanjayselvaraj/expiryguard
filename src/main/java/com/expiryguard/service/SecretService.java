package com.expiryguard.service;

import com.expiryguard.entity.Secret;
import com.expiryguard.entity.User;
import com.expiryguard.repository.SecretRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretService {
    private final SecretRepository secretRepository;

    public List<Secret> getUserSecrets(User user) {
        return secretRepository.findByUserAndActiveOrderByExpiryDateAsc(user, true);
    }

    public Secret addSecret(User user, String name, LocalDate expiryDate, String notes) {
        Secret secret = new Secret();
        secret.setUser(user);
        secret.setName(name);
        secret.setExpiryDate(expiryDate);
        secret.setNotes(notes);
        return secretRepository.save(secret);
    }

    public void deleteSecret(Long secretId, User user) {
        secretRepository.findById(secretId)
                .filter(secret -> secret.getUser().getId().equals(user.getId()))
                .ifPresent(secret -> {
                    secret.setActive(false);
                    secretRepository.save(secret);
                });
    }

    /**
     * Get all active secrets expiring within the next maxDays days.
     */
    public List<Secret> getSecretsExpiringWithin(int maxDays) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate maxDate = today.plusDays(maxDays);
        return secretRepository.findSecretsExpiringBefore(today, maxDate);
    }

    /**
     * Calculate days remaining until expiry (using UTC).
     */
    public long getDaysRemaining(Secret secret) {
        return ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), secret.getExpiryDate());
    }

    /**
     * Determine the current threshold based on days remaining.
     * 
     * @return 3 (URGENT), 7 (WARNING), 30 (NOTICE), or -1 (no notification needed)
     */
    public int getCurrentThreshold(long daysRemaining) {
        if (daysRemaining <= 3) {
            return 3; // URGENT
        } else if (daysRemaining <= 7) {
            return 7; // WARNING
        } else if (daysRemaining <= 30) {
            return 30; // NOTICE
        }
        return -1; // No notification (>30 days)
    }

    /**
     * Determine if a notification should be sent and at what threshold.
     * 
     * State-based logic:
     * - If never notified (lastNotifiedThreshold is null) → notify at current
     * threshold
     * - If currentThreshold < lastNotifiedThreshold → escalate (more urgent)
     * - Otherwise → no notification (same or less urgent threshold already sent)
     * 
     * @return threshold to notify (3, 7, 30) or -1 if no notification needed
     */
    public int getNotificationThreshold(Secret secret) {
        long daysRemaining = getDaysRemaining(secret);
        int currentThreshold = getCurrentThreshold(daysRemaining);

        // No notification if >30 days remaining
        if (currentThreshold == -1) {
            return -1;
        }

        Integer lastThreshold = secret.getLastNotifiedThreshold();

        // Never notified → send notification at current threshold
        if (lastThreshold == null) {
            log.debug("Secret '{}': never notified, will notify at {}-day threshold",
                    secret.getName(), currentThreshold);
            return currentThreshold;
        }

        // Escalation: current threshold is more urgent (lower number) than last
        if (currentThreshold < lastThreshold) {
            log.debug("Secret '{}': escalating from {}-day to {}-day threshold",
                    secret.getName(), lastThreshold, currentThreshold);
            return currentThreshold;
        }

        // Same or less urgent threshold already sent
        log.debug("Secret '{}': already notified at {}-day threshold (current: {})",
                secret.getName(), lastThreshold, currentThreshold);
        return -1;
    }

    /**
     * Mark a secret as notified at a specific threshold.
     * Call this ONLY after email send succeeds.
     * 
     * @param secret    The secret that was notified
     * @param threshold The threshold level that was notified (3, 7, or 30)
     */
    public void markAsNotified(Secret secret, int threshold) {
        secret.setLastNotifiedOn(LocalDate.now(ZoneOffset.UTC));
        secret.setLastNotifiedThreshold(threshold);
        secretRepository.save(secret);
        log.info("Marked secret '{}' as notified at {}-day threshold", secret.getName(), threshold);
    }

    /**
     * Get urgency label for logging/display.
     */
    public String getUrgencyLabel(int threshold) {
        return switch (threshold) {
            case 3 -> "URGENT";
            case 7 -> "WARNING";
            case 30 -> "NOTICE";
            default -> "UNKNOWN";
        };
    }
}