package com.khanabook.saas.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of revoked JTIs. Shared between JwtRequestFilter (reads)
 * and AuthController (writes on logout) so a token is blocked the instant
 * logout completes — no DB round-trip needed for in-flight requests.
 *
 * Tokens revoked before this JVM started (e.g. after a restart) are NOT in
 * the cache; JwtRequestFilter falls back to the DB for cache misses and
 * re-populates on the first hit.
 */
@Component
public class TokenRevocationCache {

	private static final int MAX_ENTRIES = 10_000;

	// jti -> expiresAt (epoch ms)
	private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();

	/**
	 * Mark a token as revoked immediately. Called by logout before the HTTP
	 * response is sent, so any concurrent request that already passed the
	 * cache check will still hit the DB fallback in JwtRequestFilter.
	 */
	public void revoke(String jti, long expiresAt) {
		evictExpired();
		if (cache.size() < MAX_ENTRIES) {
			cache.put(jti, expiresAt);
		}
	}

	/**
	 * Returns true if the JTI is in the cache and not yet expired.
	 * A false result does NOT mean the token is valid — the caller must
	 * still check the DB for tokens revoked before this instance started.
	 */
	public boolean isRevoked(String jti) {
		Long expiresAt = cache.get(jti);
		if (expiresAt == null) return false;
		if (expiresAt < System.currentTimeMillis()) {
			cache.remove(jti);
			return false;
		}
		return true;
	}

	/**
	 * Populate the cache from a DB hit so subsequent requests skip the DB.
	 */
	public void populateFromDb(String jti, long expiresAt) {
		if (cache.size() < MAX_ENTRIES) {
			cache.put(jti, expiresAt);
		}
	}

	private void evictExpired() {
		long now = System.currentTimeMillis();
		cache.entrySet().removeIf(e -> e.getValue() < now);
	}
}
