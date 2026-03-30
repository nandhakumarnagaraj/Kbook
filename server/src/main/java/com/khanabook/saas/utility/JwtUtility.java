package com.khanabook.saas.utility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtility {

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.expiration.ms:36000000}")
	private long expirationMs;

	private SecretKey getSigningKey() {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length >= 32) {
			return Keys.hmacShaKeyFor(secretBytes);
		}

		try {
			byte[] hashedSecret = MessageDigest.getInstance("SHA-256").digest(secretBytes);
			return Keys.hmacShaKeyFor(hashedSecret);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm unavailable for JWT key derivation", e);
		}
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
		return Jwts.builder().setSubject(username).claim("restaurantId", restaurantId).claim("role", role)
				.setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + expirationMs))
				.signWith(getSigningKey()).compact();
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
