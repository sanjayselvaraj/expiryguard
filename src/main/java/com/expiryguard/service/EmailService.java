package com.expiryguard.service;

import com.expiryguard.entity.Secret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    public void sendExpiryNotification(Secret secret) {
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), secret.getExpiryDate());

        String subject = "ExpiryGuard reminder: " + secret.getName() + " expires in " + daysRemaining + " days";
        String body = String.format(
                "Your secret '%s' will expire on %s (%d days remaining).\n\n" +
                        "Please take necessary action to renew or update it.",
                secret.getName(),
                secret.getExpiryDate(),
                daysRemaining);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(secret.getUser().getEmail());
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Email sent to {} for secret: {}", secret.getUser().getEmail(), secret.getName());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", secret.getUser().getEmail(), e.getMessage());
        }
    }

    public void sendTestEmail(String toEmail) {
        String subject = "ExpiryGuard: Test Email Notification";
        String body = String.format(
                "This is a test email from ExpiryGuard.\n\n" +
                        "If you received this email, your email configuration is working correctly!\n\n" +
                        "Test sent at: %s\n" +
                        "Environment: %s",
                java.time.LocalDateTime.now(),
                System.getProperty("spring.profiles.active", "default"));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Test email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send test email to {}", toEmail, e);
            throw new RuntimeException("Test email failed: " + e.getMessage(), e);
        }
    }
}