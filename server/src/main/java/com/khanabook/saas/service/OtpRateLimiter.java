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
 * Buckets are lazily created and never evicted (phones are bounded by the user base).
 */
@Component
public class OtpRateLimiter {

    private static final int MAX_OTP_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Returns true if the request is allowed, false if rate limit exceeded.
     */
    public boolean tryConsume(String phoneNumber) {
        Bucket bucket = buckets.computeIfAbsent(phoneNumber, k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(MAX_OTP_PER_WINDOW,
                        Refill.intervally(MAX_OTP_PER_WINDOW, WINDOW)))
                .build()
        );
        return bucket.tryConsume(1);
    }
}
