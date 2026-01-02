package com.expiryguard.controller;

import com.expiryguard.entity.Secret;
import com.expiryguard.entity.User;
import com.expiryguard.service.EmailService;
import com.expiryguard.service.SecretService;
import com.expiryguard.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {
    private final SecretService secretService;
    private final UserService userService;
    private final EmailService emailService;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @GetMapping({ "/", "/dashboard" })
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        List<Secret> secrets = secretService.getUserSecrets(user);

        // Calculate days remaining for each secret
        Map<Long, Long> daysRemaining = secrets.stream()
                .collect(Collectors.toMap(
                        Secret::getId,
                        secret -> ChronoUnit.DAYS.between(LocalDate.now(), secret.getExpiryDate())));

        // Calculate summary stats
        long expiringSoon = daysRemaining.values().stream()
                .filter(days -> days <= 7 && days > 3)
                .count();
        long urgent = daysRemaining.values().stream()
                .filter(days -> days <= 3)
                .count();

        model.addAttribute("secrets", secrets);
        model.addAttribute("daysRemaining", daysRemaining);
        model.addAttribute("expiringSoon", expiringSoon);
        model.addAttribute("urgent", urgent);
        return "dashboard";
    }

    @PostMapping("/secrets/add")
    public String addSecret(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String name,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(required = false) String notes) {
        User user = userService.findByEmail(userDetails.getUsername());
        secretService.addSecret(user, name, expiryDate, notes);
        return "redirect:/dashboard";
    }

    @PostMapping("/secrets/delete")
    public String deleteSecret(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long secretId) {
        User user = userService.findByEmail(userDetails.getUsername());
        secretService.deleteSecret(secretId, user);
        return "redirect:/dashboard";
    }

    @PostMapping("/test-email")
    public String sendTestEmail(@AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        try {
            String userEmail = userDetails.getUsername();

            // Only allow in development/test profiles or if explicitly enabled
            if (!isTestEmailAllowed()) {
                log.warn("Test email attempted in production profile by user: {}", userEmail);
                redirectAttributes.addFlashAttribute("error", "Test emails are not allowed in this environment");
                return "redirect:/dashboard";
            }

            emailService.sendTestEmail(userEmail);
            log.info("Test email sent successfully to user: {}", userEmail);
            redirectAttributes.addFlashAttribute("success", "Test email sent successfully! Check your inbox.");
        } catch (Exception e) {
            log.error("Failed to send test email to user: {}", userDetails.getUsername(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to send test email: " + e.getMessage());
        }
        return "redirect:/dashboard";
    }

    private boolean isTestEmailAllowed() {
        // Allow in H2 profile (development) or if explicitly set
        return "h2".equals(activeProfile) ||
                "test".equals(activeProfile) ||
                "development".equals(activeProfile) ||
                System.getProperty("expiryguard.test-email.enabled", "false").equals("true");
    }

}