package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Durable rate-limit tracking table.
 *
 * Each row represents one rate-limited action (OTP request, login attempt, etc.).
 * The table has indexes for efficient lookup and TTL-based cleanup.
 * Survives server restarts and works across multiple instances.
 *
 * @see com.khanabook.saas.service.DbRateLimiter
 */
@Entity
@Table(name = "rate_limit_attempts", indexes = {
    @Index(name = "idx_rate_limit_attempts_lookup", columnList = "rate_key, action_type, attempted_at"),
    @Index(name = "idx_rate_limit_attempts_cleanup", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class RateLimitAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rate_key", nullable = false, length = 255)
    private String rateKey;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public RateLimitAttempt(String rateKey, String actionType, LocalDateTime attemptedAt, LocalDateTime expiresAt) {
        this.rateKey = rateKey;
        this.actionType = actionType;
        this.attemptedAt = attemptedAt;
        this.expiresAt = expiresAt;
    }
}
