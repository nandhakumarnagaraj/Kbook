package com.khanabook.saas.config;

import com.khanabook.saas.security.RateLimitingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.springframework.core.env.Environment;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

	private final RateLimitingInterceptor rateLimitingInterceptor;
	private final Environment env;

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		if (!Arrays.asList(env.getActiveProfiles()).contains("test")) {
			registry.addInterceptor(rateLimitingInterceptor).addPathPatterns("/auth/**");
		}
	}
}
