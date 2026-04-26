package com.khanabook.saas.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtRequestFilter jwtRequestFilter;
	private final RequestIdFilter requestIdFilter;

	@Value("${cors.allowed-origins:}")
	private String allowedOriginsRaw;

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		// Easebuzz return pages are loaded by the customer's WebView after checkout.
		// The WebView's Origin is Easebuzz's domain (or null for cross-origin redirects),
		// so we allow all origins for these two static HTML safety-net pages only.
		CorsConfiguration returnPageConfig = new CorsConfiguration();
		returnPageConfig.addAllowedOriginPattern("*");
		returnPageConfig.setAllowedMethods(List.of("GET"));
		returnPageConfig.setAllowCredentials(false);
		source.registerCorsConfiguration("/payments/easebuzz/return/**", returnPageConfig);

		// Global config for all other endpoints.
		CorsConfiguration config = new CorsConfiguration();
		List<String> origins = (allowedOriginsRaw == null || allowedOriginsRaw.isBlank())
				? List.of()
				: List.of(allowedOriginsRaw.split(","))
						.stream()
						.map(String::trim)
						.filter(origin -> !origin.isBlank())
						.toList();
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Restaurant-Id"));
		config.setExposedHeaders(List.of("X-Request-Id"));
		config.setAllowCredentials(!origins.isEmpty()); // credentials only when origins are explicit
		config.setMaxAge(3600L);
		source.registerCorsConfiguration("/**", config);

		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
		return (request, response, ex) -> {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			new ObjectMapper().writeValue(response.getWriter(),
					Map.of("error", "Unauthorized", "message", ex.getMessage()));
		};
	}

	@Bean
	AccessDeniedHandler jsonAccessDeniedHandler() {
		return (request, response, ex) -> {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			new ObjectMapper().writeValue(response.getWriter(),
					Map.of("error", "Forbidden", "message", ex.getMessage()));
		};
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))

				.headers(headers -> headers
						.frameOptions(fo -> fo.deny())
						.contentTypeOptions(ct -> ct.and())
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31536000))
						.referrerPolicy(rp -> rp
								.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
						.contentSecurityPolicy(csp -> csp
								.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'")))

				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(jsonAuthenticationEntryPoint())
						.accessDeniedHandler(jsonAccessDeniedHandler()))
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Public auth endpoints (rate-limited separately)
						.requestMatchers("/auth/login", "/auth/signup", "/auth/signup/request",
								"/auth/google", "/auth/check-user",
								"/auth/reset-password", "/auth/reset-password/request",
								"/public/**",
								"/error")
						.permitAll()
						// Easebuzz webhook — called by Easebuzz with no JWT; we authenticate
						// it ourselves via reverse-hash check using the merchant salt.
						// Return pages are loaded by the customer's WebView after checkout —
						// no auth, just a static "returning to app" page (intercepted by app).
						.requestMatchers("/payments/easebuzz/webhook",
								"/payments/easebuzz/return/success",
								"/payments/easebuzz/return/failure")
						.permitAll()
						// API docs require authenticated admin access
						.requestMatchers("/docs/**", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
						.hasRole("KBOOK_ADMIN")
						// Actuator: health/readiness open, everything else authenticated
						.requestMatchers("/actuator/health", "/actuator/health/**")
						.permitAll()
						.requestMatchers("/actuator/**")
						.hasRole("KBOOK_ADMIN")
						.requestMatchers("/admin/**").hasRole("KBOOK_ADMIN")
						.requestMatchers("/business/**").hasAnyRole("OWNER", "MANAGER")
						.requestMatchers("/storefront/**").hasRole("OWNER")
						.requestMatchers("/sync/**").hasAnyRole("OWNER", "KBOOK_ADMIN")
						.requestMatchers("/restaurants/payment-config/**").hasAnyRole("OWNER", "KBOOK_ADMIN")
						.anyRequest().authenticated())

				.addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
