package com.khanabook.saas.repository;

import com.khanabook.saas.entity.RateLimitAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repository for durable rate-limit tracking.
 *
 * Provides atomic count queries and periodic cleanup of expired entries.
 * Designed to replace in-memory bucket4j rate limiters with a restart-safe
 * and multi-instance-safe alternative.
 */
@Repository
public interface RateLimitAttemptRepository extends JpaRepository<RateLimitAttempt, Long> {

    /**
     * Count recent attempts for a given rate key and action type within the
     * active window. The caller provides the cutoff timestamp (typically
     * {@code NOW() - windowDuration}) to count only non-expired attempts.
     *
     * @param rateKey   the rate-limit key (e.g. "otp:+919876543210", "login:192.168.1.1")
     * @param actionType the action type (e.g. "OTP_REQUEST", "LOGIN_ATTEMPT")
     * @param since     the cutoff time; attempts older than this are excluded
     * @return count of attempts within the window
     */
    @Query("SELECT COUNT(a) FROM RateLimitAttempt a " +
           "WHERE a.rateKey = :rateKey " +
           "  AND a.actionType = :actionType " +
           "  AND a.attemptedAt > :since")
    long countRecentAttempts(
            @Param("rateKey") String rateKey,
            @Param("actionType") String actionType,
            @Param("since") LocalDateTime since);

    /**
     * Acquire a PostgreSQL advisory transaction lock for the given lock ID.
     * Uses pg_advisory_xact_lock which is automatically released when the
     * current transaction commits or rolls back.
     *
     * @param lockId a bigint lock identifier derived from the rate key
     */
    @Query(value = "SELECT pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    void acquireAdvisoryLock(@Param("lockId") long lockId);

    /**
     * Remove all expired rows. Called periodically (e.g. by a scheduled task
     * or before each rate-limit check) to prevent unbounded table growth.
     *
     * @param before cutoff timestamp; rows with expires_at older than this are deleted
     * @return number of deleted rows
     */
    int deleteByExpiresAtBefore(LocalDateTime before);
}
