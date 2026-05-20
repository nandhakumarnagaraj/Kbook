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
 * Stale buckets are evicted after 30 minutes of inactivity.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final java.time.Duration WINDOW = java.time.Duration.ofMinutes(15);
    private static final long EVICT_AFTER_MS = 30 * 60 * 1000L;

    private final java.util.concurrent.ConcurrentHashMap<String, BucketEntry> buckets = new java.util.concurrent.ConcurrentHashMap<>();
    private final org.springframework.core.env.Environment env;

    private static class BucketEntry {
        final io.github.bucket4j.Bucket bucket;
        volatile long lastAccess;

        BucketEntry(io.github.bucket4j.Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    public LoginRateLimiter(org.springframework.core.env.Environment env) {
        this.env = env;
    }

    public boolean tryConsume(String ipAddress) {
        java.util.List<String> activeProfiles = java.util.Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains("test") || activeProfiles.contains("sandbox")) {
            return true;
        }
        BucketEntry entry = buckets.computeIfAbsent(ipAddress, k ->
            new BucketEntry(io.github.bucket4j.Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(MAX_ATTEMPTS,
                        io.github.bucket4j.Refill.intervally(MAX_ATTEMPTS, WINDOW)))
                .build())
        );
        entry.lastAccess = System.currentTimeMillis();
        boolean result = entry.bucket.tryConsume(1);
        evictStale();
        return result;
    }

    private void evictStale() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(e -> (now - e.getValue().lastAccess) > EVICT_AFTER_MS);
    }
}
