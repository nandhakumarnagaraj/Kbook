package com.khanabook.saas.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtRequestFilter jwtRequestFilter;

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)

				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Public auth endpoints (rate-limited separately)
						.requestMatchers("/auth/login", "/auth/signup", "/auth/signup/request",
								"/auth/google", "/auth/check-user",
								"/auth/reset-password", "/auth/reset-password/request",
								"/error")
						.permitAll()
						// API docs — admin-only (owners must not enumerate endpoints)
						.requestMatchers("/docs/**", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
						.hasRole("KBOOK_ADMIN")
						// Actuator: health/readiness open, everything else authenticated
						.requestMatchers("/actuator/health", "/actuator/health/**")
						.permitAll()
						.requestMatchers("/actuator/**")
						.hasRole("KBOOK_ADMIN")
						.requestMatchers("/admin/**").hasRole("KBOOK_ADMIN")
						.requestMatchers("/sync/**").hasAnyRole("OWNER", "KBOOK_ADMIN")
						.anyRequest().authenticated())

				.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
