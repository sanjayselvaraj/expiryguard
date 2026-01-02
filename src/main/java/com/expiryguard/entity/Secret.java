package com.expiryguard.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "secrets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Secret {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate expiryDate;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Audit field: when the last notification was sent.
     * Kept for logging/debugging purposes only.
     */
    @Column
    private LocalDate lastNotifiedOn;

    /**
     * State field: which threshold level was last notified (30, 7, 3, or null).
     * Used for escalation logic - notification fires when:
     * - lastNotifiedThreshold is null (never notified), OR
     * - currentThreshold < lastNotifiedThreshold (escalation to more urgent level)
     */
    @Column
    private Integer lastNotifiedThreshold;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}