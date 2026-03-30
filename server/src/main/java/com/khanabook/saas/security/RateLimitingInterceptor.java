package com.khanabook.saas.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import java.time.Duration;

@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

	private final Map<String, Bucket> buckets = new java.util.LinkedHashMap<String, Bucket>(1001, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
			return size() > 1000;
		}
	};

	private Bucket createNewBucket() {

		Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		String ip = request.getRemoteAddr();
		Bucket bucket;
		synchronized (buckets) {
			bucket = buckets.computeIfAbsent(ip, k -> createNewBucket());
		}

		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
			return true;
		} else {
			long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
			response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
			response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many requests. Please try again later.");
			return false;
		}
	}
}
