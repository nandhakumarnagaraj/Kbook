package com.khanabook.saas.utility;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtility {

	private static final Logger log = LoggerFactory.getLogger(JwtUtility.class);

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.expiration.ms:36000000}")
	private long expirationMs;

	private SecretKey getSigningKey() {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length >= 32) {
			return Keys.hmacShaKeyFor(secretBytes);
		}
		// Refuse to start with a weak secret. Hashing a short secret with SHA-256 does not
		// add cryptographic strength — an attacker who knows the secret space is small can
		// brute-force the original and re-derive the hash. Fail fast instead.
		throw new IllegalStateException(
			"JWT_SECRET is only " + secretBytes.length + " bytes. " +
			"A minimum of 32 cryptographically random bytes is required. " +
			"Set a strong JWT_SECRET environment variable before starting the server.");
	}

	public Long extractRestaurantId(String token) {
		final Claims claims = extractAllClaims(token);
		return claims.get("restaurantId", Long.class);
	}

	public String extractRole(String token) {
		final Claims claims = extractAllClaims(token);
		return claims.get("role", String.class);
	}

	public String extractUsername(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
	}

	public String generateToken(String username, Long restaurantId, String role) {
		return generateToken(username, restaurantId, role, null);
	}

	public String generateToken(String username, Long restaurantId, String role, String deviceId) {
		var builder = Jwts.builder()
				.setId(java.util.UUID.randomUUID().toString())
				.setSubject(username)
				.claim("restaurantId", restaurantId)
				.claim("role", role)
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + expirationMs));
		if (deviceId != null && !deviceId.isBlank()) {
			builder.claim("deviceId", deviceId);
		}
		return builder.signWith(getSigningKey()).compact();
	}

	public String extractDeviceId(String token) {
		try {
			return extractAllClaims(token).get("deviceId", String.class);
		} catch (Exception e) {
			return null;
		}
	}

	public String extractJti(String token) {
		return extractClaim(token, Claims::getId);
	}

	public Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}

	public Date extractIssuedAt(String token) {
		try {
			return extractClaim(token, Claims::getIssuedAt);
		} catch (Exception e) {
			return null;
		}
	}

	public Boolean isTokenExpired(String token) {
		try {
			return extractClaim(token, Claims::getExpiration).before(new Date());
		} catch (io.jsonwebtoken.ExpiredJwtException e) {
			return true;
		} catch (Exception e) {
			return true;
		}
	}
}
