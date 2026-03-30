package com.khanabook.saas;

import com.khanabook.saas.debug.DebugNDJSONLogger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class KhanaBookSaaSApplication {
	public static void main(String[] args) {
		// Marker to verify debug logger + log path works at runtime.
		DebugNDJSONLogger.log(
				"startup",
				"H0_LOGGER_STARTUP",
				"KhanaBookSaaSApplication:main",
				"Server starting",
				java.util.Map.of("cwd", System.getProperty("user.dir"))
		);
		SpringApplication.run(KhanaBookSaaSApplication.class, args);
	}
}
