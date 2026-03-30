package com.khanabook.saas.security;

import com.khanabook.saas.debug.DebugNDJSONLogger;
import com.khanabook.saas.utility.JwtUtility;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.repository.UserRepository;
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
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

	private final JwtUtility jwtUtility;
	private final UserRepository userRepository;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		final String authorizationHeader = request.getHeader("Authorization");
		final String path = request.getRequestURI();

		// Temporary bypass for testing
		if ("Bearer TEST_MODE".equals(authorizationHeader)) {
			com.khanabook.saas.security.TenantContext.setCurrentTenant(5874635834291080177L);
			com.khanabook.saas.security.TenantContext.setCurrentRole("OWNER");
			org.springframework.security.core.authority.SimpleGrantedAuthority authority = new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OWNER");
			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken("test@example.com", null, java.util.Collections.singletonList(authority));
			authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authToken);
			chain.doFilter(request, response);
			return;
		}

		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			String jwt = authorizationHeader.substring(7);

			boolean tokenExpired = true;
			boolean jwtExtractOk = false;
			Long restaurantIdPresent = null;
			boolean tenantSet = false;

			try {
				tokenExpired = jwtUtility.isTokenExpired(jwt);
				if (!tokenExpired) {
					Long restaurantId = jwtUtility.extractRestaurantId(jwt);
					String username = jwtUtility.extractUsername(jwt);
					jwtExtractOk = true;
					restaurantIdPresent = restaurantId;

					if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

						User user = userRepository.findByLoginId(username)
								.or(() -> userRepository.findByEmail(username))
								.or(() -> userRepository.findByWhatsappNumber(username))
								.orElse(null);

						if (user != null && Boolean.TRUE.equals(user.getIsActive())) {
							if (restaurantId != null) {
								TenantContext.setCurrentTenant(restaurantId);
								tenantSet = true;
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

				logger.debug("JWT validation failed: " + e.getClass().getSimpleName());
			}

			DebugNDJSONLogger.log(
					"pre-debug",
					"H1_JWT_MISSING_OR_INVALID",
					"JwtRequestFilter:doFilterInternal",
					"JWT auth header inspected",
					java.util.Map.of(
							"path", path,
							"authorizationHeaderPresent", true,
							"tokenExpired", tokenExpired,
							"jwtExtractOk", jwtExtractOk,
							"restaurantIdPresent", restaurantIdPresent != null,
							"tenantSet", tenantSet
					)
			);
		}
		else {
			DebugNDJSONLogger.log(
					"pre-debug",
					"H1_JWT_MISSING_OR_INVALID",
					"JwtRequestFilter:doFilterInternal",
					"JWT auth header missing or not Bearer",
					java.util.Map.of(
							"path", path,
							"authorizationHeaderPresent", false
					)
			);
		}

		try {
			chain.doFilter(request, response);
		} finally {

			TenantContext.clear();
			SecurityContextHolder.clearContext();
		}
	}
}
