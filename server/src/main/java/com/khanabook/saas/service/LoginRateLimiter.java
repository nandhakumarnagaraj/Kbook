package com.khanabook.saas.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Per-IP rate limiter for /auth/login to prevent brute-force attacks.
 * Allows at most 10 attempts per IP per 15 minutes.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_BUCKETS = 5000;

    private final Map<String, Bucket> buckets = new java.util.LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > MAX_BUCKETS;
        }
    };

    public synchronized boolean tryConsume(String ipAddress) {
        Bucket bucket = buckets.computeIfAbsent(ipAddress, k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(MAX_ATTEMPTS,
                        Refill.intervally(MAX_ATTEMPTS, WINDOW)))
                .build()
        );
        return bucket.tryConsume(1);
    }
}
