package com.khanabook.saas.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;

@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    // Auth endpoints: 5 requests/minute per IP (OTP brute-force protection)
    private static final Bandwidth AUTH_LIMIT =
            Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));

    // Sync endpoints: 30 requests/minute per IP (prevents sync-based DoS)
    private static final Bandwidth SYNC_LIMIT =
            Bandwidth.classic(30, Refill.greedy(30, Duration.ofMinutes(1)));

    // Separate LRU caches per bucket type to keep eviction isolated
    private final Map<String, Bucket> authBuckets  = createLRUMap();
    private final Map<String, Bucket> syncBuckets  = createLRUMap();

    private static Map<String, Bucket> createLRUMap() {
        return new java.util.LinkedHashMap<>(1001, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > 1000;
            }
        };
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String ip = resolveClientIp(request);
        boolean isSyncPath = request.getRequestURI().contains("/sync/");

        Bucket bucket;
        if (isSyncPath) {
            synchronized (syncBuckets) {
                bucket = syncBuckets.computeIfAbsent(ip,
                        k -> Bucket.builder().addLimit(SYNC_LIMIT).build());
            }
        } else {
            synchronized (authBuckets) {
                bucket = authBuckets.computeIfAbsent(ip,
                        k -> Bucket.builder().addLimit(AUTH_LIMIT).build());
            }
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        long retryAfter = probe.getNanosToWaitForRefill() / 1_000_000_000;
        response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfter));
        response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too many requests. Please try again later.");
        return false;
    }

    /**
     * Respects X-Forwarded-For so rate limiting works correctly behind
     * a reverse proxy or load balancer.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Take only the first (original client) IP from the chain
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
