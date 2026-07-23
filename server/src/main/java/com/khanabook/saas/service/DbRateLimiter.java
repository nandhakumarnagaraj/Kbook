package com.khanabook.saas.service;

import com.khanabook.saas.entity.RateLimitAttempt;
import com.khanabook.saas.repository.RateLimitAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Durable, restart-safe and multi-instance-safe rate limiter backed by the
 * {@code rate_limit_attempts} table.
 *
 * <p>Replaces the in-memory bucket4j implementations ({@link OtpRateLimiter},
 * {@link LoginRateLimiter}, {@link
 * com.khanabook.saas.security.RateLimitingInterceptor}) for scenarios that
 * require the rate-limit state to survive a server restart or to be shared
 * across multiple server instances.
 *
 * <p>Usage:
 * <pre>{@code
 * DbRateLimiter limiter = new DbRateLimiter(repository, "OTP_REQUEST", 5, Duration.ofMinutes(10));
 * if (limiter.tryConsume("+919876543210")) {
 *     // send OTP
 * } else {
 *     // rate limit exceeded
 * }
 * }</pre>
 *
 * <p>Each call to {@link #tryConsume(String)} performs three database operations:
 * <ol>
 *   <li>Acquire a transaction-scoped advisory lock for the key.</li>
 *   <li>Count recent attempts for the key within the active window.</li>
 *   <li>If under the limit, insert a new attempt row.</li>
 * </ol>
 * The advisory lock makes the check-and-insert sequence atomic for each key
 * across all application instances sharing the database.
 */
public class DbRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(DbRateLimiter.class);

    private final RateLimitAttemptRepository repository;
    private final String actionType;
    private final int maxAttempts;
    private final Duration window;
    private final boolean advisoryLocksEnabled;

    // Cleanup runs at most once every 60 seconds to avoid hammering the DB
    private static final long CLEANUP_INTERVAL_MS = 60_000L;
    private volatile long lastCleanupMs = 0L;

    /**
     * Creates a database-backed rate limiter.
     *
     * @param repository   the rate-limit repository
     * @param actionType   label for the type of action being rate-limited
     *                     (e.g. "OTP_REQUEST", "LOGIN_ATTEMPT")
     * @param maxAttempts  maximum allowed attempts within the window
     * @param window       the sliding time window
     */
    public DbRateLimiter(RateLimitAttemptRepository repository,
                         String actionType,
                         int maxAttempts,
                         Duration window,
                         boolean advisoryLocksEnabled) {
        this.repository = repository;
        this.actionType = actionType;
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.advisoryLocksEnabled = advisoryLocksEnabled;
    }

    /**
     * Returns true if the action is allowed (under the rate limit).
     *
     * Uses a PostgreSQL advisory transaction lock scoped to this rate key
     * to prevent TOCTOU races under concurrent access. The lock is
     * automatically released when the transaction commits.
     *
     * Lock scope: a deterministic hash of (actionType, rateKey) so that
     * concurrent requests for the same rate key serialize while different
     * keys are unaffected.
     */
    @org.springframework.transaction.annotation.Transactional
    public boolean tryConsume(String rateKey) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minus(window);
        LocalDateTime expiresAt = now.plus(window);

        // Opportunistic cleanup of expired rows (throttled)
        cleanupIfNeeded();

        // Acquire a PostgreSQL advisory transaction lock scoped to this rate key.
        // This serialises concurrent requests for the same key so the
        // check-then-insert sequence is atomic within the transaction.
        if (advisoryLocksEnabled) {
            long lockId = actionType.hashCode() * 31L + rateKey.hashCode();
            repository.acquireAdvisoryLock(lockId);
        }

        long recentCount = repository.countRecentAttempts(rateKey, actionType, since);
        if (recentCount >= maxAttempts) {
            log.debug("Rate limit exceeded key={} action={} count={} max={}",
                    maskKey(rateKey), actionType, recentCount, maxAttempts);
            return false;
        }

        repository.save(new RateLimitAttempt(rateKey, actionType, now, expiresAt));
        return true;
    }

    /**
     * Periodic cleanup of expired rows. Throttled to run at most once per
     * {@link #CLEANUP_INTERVAL_MS}.
     */
    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupMs < CLEANUP_INTERVAL_MS) {
            return;
        }
        synchronized (this) {
            if (now - lastCleanupMs < CLEANUP_INTERVAL_MS) {
                return;
            }
            try {
                int deleted = repository.deleteByExpiresAtBefore(LocalDateTime.now());
                if (deleted > 0) {
                    log.debug("Cleaned up {} expired rate-limit rows", deleted);
                }
            } catch (Exception e) {
                log.warn("Rate-limit cleanup failed (non-critical)", e);
            }
            lastCleanupMs = System.currentTimeMillis();
        }
    }

    /**
     * Masks the rate key for logging: shows action type + first few chars.
     */
    private String maskKey(String rateKey) {
        if (rateKey == null) return "null";
        if (rateKey.length() <= 6) return actionType + ":" + rateKey.substring(0, Math.min(3, rateKey.length())) + "***";
        return actionType + ":" + rateKey.substring(0, 3) + "***" + rateKey.substring(rateKey.length() - 2);
    }

    public String actionType() {
        return actionType;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration window() {
        return window;
    }
}
