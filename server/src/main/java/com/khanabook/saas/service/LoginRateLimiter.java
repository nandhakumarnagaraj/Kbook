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
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String ipAddress) {
        Bucket bucket = buckets.computeIfAbsent(ipAddress, k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(MAX_ATTEMPTS,
                        Refill.intervally(MAX_ATTEMPTS, WINDOW)))
                .build()
        );
        return bucket.tryConsume(1);
    }
}
