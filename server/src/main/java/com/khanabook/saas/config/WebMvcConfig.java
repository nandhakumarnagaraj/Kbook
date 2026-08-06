package com.khanabook.saas.config;

import com.khanabook.saas.security.RateLimitingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.springframework.core.env.Environment;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

	private final RateLimitingInterceptor rateLimitingInterceptor;
	private final Environment env;

	@Value("${kbook.cdn.base-path}")
	private String cdnBasePath;

	@Override
	public void addResourceHandlers(@org.springframework.lang.NonNull ResourceHandlerRegistry registry) {
		String absolutePath = Paths.get(cdnBasePath).toAbsolutePath().normalize().toUri().toString();
		registry.addResourceHandler("/cdn/**")
				.addResourceLocations(absolutePath)
				.setCachePeriod(3600);
	}

	@Override
	public void addInterceptors(@org.springframework.lang.NonNull InterceptorRegistry registry) {
		List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
		if (!activeProfiles.contains("test") && !activeProfiles.contains("sandbox")) {
			registry.addInterceptor(rateLimitingInterceptor)
					.addPathPatterns("/auth/**", "/sync/**")
					.excludePathPatterns("/auth/google");
		}
	}
}
