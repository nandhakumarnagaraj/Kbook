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
	private final TerminalRequestFilter terminalRequestFilter;

	@Value("${cors.allowed-origins:}")
	private String allowedOriginsRaw;

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

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
		config.setAllowedHeaders(List.of(
				"Authorization",
				"Content-Type",
				"X-Restaurant-Id",
				"X-App-Platform",
				"X-App-Version"));
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
								.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: https://cdn.kbook.iadv.cloud; frame-ancestors 'none'")))

				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(jsonAuthenticationEntryPoint())
						.accessDeniedHandler(jsonAccessDeniedHandler()))
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Public auth endpoints (rate-limited separately)
						.requestMatchers("/auth/login", "/auth/signup", "/auth/signup/request",
								"/auth/google", "/auth/check-user",
								"/auth/reset-password", "/auth/reset-password/request",
								"/auth/forgot-password/request-otp",
								"/auth/forgot-password/verify-otp",
								"/auth/forgot-password/reset-password",
								"/public/**",
								"/error",
								"/docs/**", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
						.permitAll()

						// Actuator: health/readiness open, everything else authenticated
						.requestMatchers("/actuator/health", "/actuator/health/**")
						.permitAll()
						.requestMatchers("/actuator/**")
						.hasRole("KBOOK_ADMIN")
						.requestMatchers("/admin/**").hasRole("KBOOK_ADMIN")
						// Terminal management: OWNER and SHOP_ADMIN can manage devices
						.requestMatchers("/business/terminals/**", "/business/terminal-requests/**")
						.hasAnyRole("OWNER", "SHOP_ADMIN")
						// General business APIs: OWNER only (dashboard, orders, menu, staff, refunds)
						.requestMatchers("/business/**").hasRole("OWNER")
						// Terminal onboarding: OWNER and SHOP_ADMIN (activate, list, reclaim, request-status, complete)
						.requestMatchers("/sync/terminal/**")
						.hasAnyRole("OWNER", "SHOP_ADMIN")
						// Master pull: KBOOK_ADMIN may read for support (uses restaurantId override)
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/sync/master/pull")
						.hasAnyRole("OWNER", "KBOOK_ADMIN")
						// Operational sync (bills, menu, payments, master, profile, stock, users, categories):
						// OWNER only. SHOP_ADMIN and KBOOK_ADMIN must not push or mutate restaurant data.
						.requestMatchers("/sync/**").hasRole("OWNER")
						.requestMatchers("/restaurants/logo").hasAnyRole("OWNER", "KBOOK_ADMIN")
						.anyRequest().authenticated())

				.addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
				// Terminal token validation must run AFTER JwtRequestFilter so the tenant is
				// already on TenantContext; otherwise the restaurant/terminal mismatch cross-check
				// (which reads TenantContext.getCurrentTenant()) can never fire.
				.addFilterAfter(terminalRequestFilter, JwtRequestFilter.class);

		return http.build();
	}
}
