package com.khanabook.saas.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiter for /auth/login to prevent brute-force attacks.
 * Allows at most 10 attempts per IP per 15 minutes.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final java.time.Duration WINDOW = java.time.Duration.ofMinutes(15);

    private final java.util.concurrent.ConcurrentHashMap<String, io.github.bucket4j.Bucket> buckets = new java.util.concurrent.ConcurrentHashMap<>();
    private final org.springframework.core.env.Environment env;

    public LoginRateLimiter(org.springframework.core.env.Environment env) {
        this.env = env;
    }

    public boolean tryConsume(String ipAddress) {
        java.util.List<String> activeProfiles = java.util.Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains("test") || activeProfiles.contains("sandbox")) {
            return true;
        }
        io.github.bucket4j.Bucket bucket = buckets.computeIfAbsent(ipAddress, k ->
            io.github.bucket4j.Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(MAX_ATTEMPTS,
                        io.github.bucket4j.Refill.intervally(MAX_ATTEMPTS, WINDOW)))
                .build()
        );
        return bucket.tryConsume(1);
    }
}
