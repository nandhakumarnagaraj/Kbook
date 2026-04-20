package com.khanabook.saas.security;

import com.khanabook.saas.utility.JwtUtility;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.repository.TokenBlocklistRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

	private final JwtUtility jwtUtility;
	private final UserRepository userRepository;
	private final TokenBlocklistRepository tokenBlocklistRepository;
	private final TokenRevocationCache tokenRevocationCache;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		final String authorizationHeader = request.getHeader("Authorization");

		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			String jwt = authorizationHeader.substring(7);

			try {
				if (!jwtUtility.isTokenExpired(jwt)) {
					// Check token revocation — shared cache first (zero-latency for tokens
					// revoked since this JVM started), then DB fallback for older revocations.
					String jti = jwtUtility.extractJti(jwt);
					if (jti != null) {
						boolean revoked = tokenRevocationCache.isRevoked(jti);
						if (!revoked && tokenBlocklistRepository.existsByJti(jti)) {
							long expiresAt = jwtUtility.extractExpiration(jwt) != null
									? jwtUtility.extractExpiration(jwt).getTime()
									: System.currentTimeMillis() + 3600_000L;
							tokenRevocationCache.populateFromDb(jti, expiresAt);
							revoked = true;
						}
						if (revoked) {
							response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
							return;
						}
					}

					Long restaurantId = jwtUtility.extractRestaurantId(jwt);
					String username = jwtUtility.extractUsername(jwt);

					if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

						User user = userRepository.findByPhoneNumber(username)
								.or(() -> userRepository.findByLoginId(username))
								.or(() -> userRepository.findByEmail(username))
								.or(() -> userRepository.findByWhatsappNumber(username))
								.orElse(null);

						if (user != null && Boolean.TRUE.equals(user.getIsActive())) {
							// Reject tokens issued before a password reset
							if (user.getTokenInvalidatedAt() != null) {
								java.util.Date issuedAt = jwtUtility.extractIssuedAt(jwt);
								if (issuedAt != null && issuedAt.getTime() < user.getTokenInvalidatedAt()) {
									response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalidated by password reset");
									return;
								}
							}
							if (restaurantId != null) {
								TenantContext.setCurrentTenant(restaurantId);
							}

							String role = user.getRole().name();
							TenantContext.setCurrentRole(role);

							org.springframework.security.core.authority.SimpleGrantedAuthority authority = new org.springframework.security.core.authority.SimpleGrantedAuthority(
									"ROLE_" + role);

							UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
									username, null, java.util.Collections.singletonList(authority));
							authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
							SecurityContextHolder.getContext().setAuthentication(authToken);
						}
					}
				}
			} catch (Exception e) {
				logger.warn("JWT validation failed: {}", e.getClass().getSimpleName());
			}

		}


		try {
			chain.doFilter(request, response);
		} finally {

			TenantContext.clear();
			SecurityContextHolder.clearContext();
		}
	}
}
