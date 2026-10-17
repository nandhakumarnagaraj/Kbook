-- Persistent rate-limit tracking table.
-- Replaces the in-memory bucket4j rate limiters (OtpRateLimiter, LoginRateLimiter,
-- RateLimitingInterceptor) with a durable, restart-safe and multi-instance-safe
-- implementation.
--
-- Each rate-limited action (OTP request, login attempt, etc.) inserts a row.
-- The count of recent attempts within the TTL window determines whether the
-- action is allowed. Expired rows are cleaned up by a periodic sweep.
--
-- The index on (rate_key, action_type, attempted_at) supports the lookup query:
--   SELECT COUNT(*) FROM rate_limit_attempts
--   WHERE rate_key = :key AND action_type = :type AND attempted_at > NOW() - :window
--
-- The index on expires_at supports efficient cleanup:
--   DELETE FROM rate_limit_attempts WHERE expires_at < NOW()

CREATE TABLE IF NOT EXISTS rate_limit_attempts (
    id BIGSERIAL PRIMARY KEY,
    rate_key VARCHAR(255) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rate_limit_attempts_lookup
    ON rate_limit_attempts (rate_key, action_type, attempted_at);

CREATE INDEX IF NOT EXISTS idx_rate_limit_attempts_cleanup
    ON rate_limit_attempts (expires_at);
