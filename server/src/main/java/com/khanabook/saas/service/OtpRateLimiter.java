package com.khanabook.saas.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-phone-number rate limiter for OTP endpoints.
 * Allows at most 5 OTP requests per phone number per 10 minutes.
 * Stale buckets are evicted after 30 minutes of inactivity.
 */
@Component
public class OtpRateLimiter {

    private static final int MAX_OTP_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final long EVICT_AFTER_MS = 30 * 60 * 1000L;

    private static class BucketEntry {
        final Bucket bucket;
        volatile long lastAccess;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    /**
     * Returns true if the request is allowed, false if rate limit exceeded.
     */
    public boolean tryConsume(String phoneNumber) {
        BucketEntry entry = buckets.computeIfAbsent(phoneNumber, k ->
            new BucketEntry(Bucket.builder()
                .addLimit(Bandwidth.classic(MAX_OTP_PER_WINDOW,
                        Refill.intervally(MAX_OTP_PER_WINDOW, WINDOW)))
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
