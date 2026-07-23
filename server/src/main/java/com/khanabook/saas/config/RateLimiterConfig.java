package com.khanabook.saas.config;

import com.khanabook.saas.repository.RateLimitAttemptRepository;
import com.khanabook.saas.service.DbRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;
import java.sql.SQLException;

/**
 * Provides database-backed rate limiter beans for various action types.
 *
 * <p>These replace the in-memory bucket4j implementations
 * ({@link com.khanabook.saas.service.OtpRateLimiter},
 * {@link com.khanabook.saas.service.LoginRateLimiter}) with durable,
 * restart-safe alternatives.
 *
 * <p>Existing in-memory beans are retained for backward compatibility and
 * can be removed once all environments have migrated to the DB-backed version.
 */
@Configuration
public class RateLimiterConfig {

    private final boolean advisoryLocksEnabled;

    public RateLimiterConfig(DataSource dataSource) {
        this.advisoryLocksEnabled = supportsPostgresAdvisoryLocks(dataSource);
    }

    /**
     * OTP request rate limiter: 5 attempts per phone number per 10 minutes.
     * Matches the existing {@code OtpRateLimiter} configuration.
     */
    @Bean
    public DbRateLimiter otpRateLimiterDb(RateLimitAttemptRepository repository) {
        return new DbRateLimiter(
                repository,
                "OTP_REQUEST",
                5,
                Duration.ofMinutes(10),
                advisoryLocksEnabled);
    }

    /**
     * Login attempt rate limiter: 10 attempts per IP per 15 minutes.
     * Matches the existing {@code LoginRateLimiter} configuration.
     */
    @Bean
    public DbRateLimiter loginRateLimiterDb(RateLimitAttemptRepository repository) {
        return new DbRateLimiter(
                repository,
                "LOGIN_ATTEMPT",
                10,
                Duration.ofMinutes(15),
                advisoryLocksEnabled);
    }

    private static boolean supportsPostgresAdvisoryLocks(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(
                    connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Unable to determine database capabilities for rate limiting",
                    e);
        }
    }
}
