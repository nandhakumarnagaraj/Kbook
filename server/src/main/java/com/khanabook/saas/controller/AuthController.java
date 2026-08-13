package com.khanabook.saas.controller;

import com.khanabook.saas.entity.TokenBlocklist;
import com.khanabook.saas.repository.TokenBlocklistRepository;
import com.khanabook.saas.security.TokenRevocationCache;
import com.khanabook.saas.service.AuthService;
import com.khanabook.saas.service.DbRateLimiter;
import com.khanabook.saas.service.LoginRateLimiter;
import com.khanabook.saas.service.OtpRateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import com.khanabook.saas.service.WebAdminPasswordResetService;
import com.khanabook.saas.utility.JwtUtility;
import com.khanabook.saas.webadmin.dto.RequestOtpRequest;
import com.khanabook.saas.webadmin.dto.VerifyOtpRequest;
import com.khanabook.saas.webadmin.dto.VerifyOtpResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

@RestController
@Validated
@RequestMapping("/auth")
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final AuthService authService;
	private final JwtUtility jwtUtility;
	private final TokenBlocklistRepository tokenBlocklistRepository;
	private final TokenRevocationCache tokenRevocationCache;
	private final OtpRateLimiter otpRateLimiter;
	private final LoginRateLimiter loginRateLimiter;
	private final DbRateLimiter otpRateLimiterDb;
	private final DbRateLimiter loginRateLimiterDb;
	private final WebAdminPasswordResetService webAdminPasswordResetService;

	public AuthController(AuthService authService,
	                      JwtUtility jwtUtility,
	                      TokenBlocklistRepository tokenBlocklistRepository,
	                      TokenRevocationCache tokenRevocationCache,
	                      OtpRateLimiter otpRateLimiter,
	                      LoginRateLimiter loginRateLimiter,
	                      @Qualifier("otpRateLimiterDb") DbRateLimiter otpRateLimiterDb,
	                      @Qualifier("loginRateLimiterDb") DbRateLimiter loginRateLimiterDb,
	                      WebAdminPasswordResetService webAdminPasswordResetService) {
		this.authService = authService;
		this.jwtUtility = jwtUtility;
		this.tokenBlocklistRepository = tokenBlocklistRepository;
		this.tokenRevocationCache = tokenRevocationCache;
		this.otpRateLimiter = otpRateLimiter;
		this.loginRateLimiter = loginRateLimiter;
		this.otpRateLimiterDb = otpRateLimiterDb;
		this.loginRateLimiterDb = loginRateLimiterDb;
		this.webAdminPasswordResetService = webAdminPasswordResetService;
	}

	/**
	 * Returns true if the action is under all configured rate limits.
	 * Checks both the in-memory (legacy) and DB-backed rate limiters.
	 */
	private boolean isOtpAllowed(String key) {
		return otpRateLimiter.tryConsume(key) && otpRateLimiterDb.tryConsume(key);
	}

	/**
	 * Returns true if the login action is under all configured rate limits.
	 */
	private boolean isLoginAllowed(String ip) {
		return loginRateLimiter.tryConsume(ip) && loginRateLimiterDb.tryConsume(ip);
	}

	/**
	 * Per-account login rate limit — blocks brute-force on a specific account
	 * even from distributed IPs. Limit: 5 attempts per 15 minutes per loginId.
	 */
	private boolean isAccountLoginAllowed(String loginId) {
		if (loginId == null || loginId.isBlank()) return true;
		return loginRateLimiterDb.tryConsume("acct:" + loginId.toLowerCase().trim());
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		String ip = getClientIp(httpRequest);
		if (!isLoginAllowed(ip)) {
			log.warn("Login rate limit exceeded ip={}", ip);
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		if (!isAccountLoginAllowed(request.getLoginId())) {
			log.warn("Login rate limit exceeded account={}", request.getLoginId() != null ? request.getLoginId().substring(0, Math.min(4, request.getLoginId().length())) + "***" : "null");
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		log.debug("Login attempt identifierLen={} ip={}", request.getLoginId() == null ? 0 : request.getLoginId().length(), ip);
		return ResponseEntity.ok(authService.login(request));
	}

	private String getClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
		log.debug("Signup attempt phoneLen={}", request.getPhoneNumber() == null ? 0 : request.getPhoneNumber().length());
		return ResponseEntity.ok(authService.signup(request));
	}

	@Profile("dev")
	@PostMapping("/signup/dev")
	public ResponseEntity<AuthResponse> devSignup(@RequestBody Map<String, String> body) {
		SignupRequest req = new SignupRequest();
		req.setPhoneNumber(body.getOrDefault("phoneNumber", "7000000001"));
		req.setName(body.getOrDefault("name", "Dev User"));
		req.setPassword(body.getOrDefault("password", "admin123"));
		req.setOtp("123456");
		req.setDeviceId(body.getOrDefault("deviceId", "DEV"));
		return ResponseEntity.ok(authService.devSignup(req));
	}

	@Profile("dev")
	@PostMapping("/signup/dev-admin")
	public ResponseEntity<AuthResponse> devAdminSignup(@RequestBody Map<String, String> body) {
		return ResponseEntity.ok(authService.devAdminSignup(body));
	}

	@PostMapping("/signup/request")
	public ResponseEntity<Void> requestSignupOtp(@Valid @RequestBody SignupOtpRequest request, HttpServletRequest httpRequest) {
		String ip = getClientIp(httpRequest);
		if (!isOtpAllowed(request.getPhoneNumber()) || !isOtpAllowed("ip:" + ip)) {
			log.warn("OTP rate limit exceeded for signup phone={}*** ip={}", request.getPhoneNumber().substring(0, 3), ip);
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		authService.requestSignupOtp(request.getPhoneNumber());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/google")
	public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
			HttpServletRequest httpRequest) {
		String ip = getClientIp(httpRequest);
		if (!isLoginAllowed(ip)) {
			log.warn("Google login rate limit exceeded ip={}", ip);
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		return ResponseEntity.ok(authService.googleLogin(request));
	}

	@GetMapping("/check-user")
	public ResponseEntity<Boolean> checkUser(@RequestParam String phoneNumber) {
		// Always returns true to prevent user enumeration attacks.
		// The Android client handles both existing and new user flows gracefully.
		if (phoneNumber == null || !phoneNumber.matches("^\\d{10}$")) {
			return ResponseEntity.ok(false);
		}
		return ResponseEntity.ok(true);
	}

	@PostMapping("/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request.getPhoneNumber(), request.getOtp(), request.getNewPassword());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/change-password")
	public ResponseEntity<Void> changePassword(
			java.security.Principal principal,
			@Valid @RequestBody ChangePasswordRequest request
	) {
		authService.changePassword(principal.getName(), request.getCurrentPassword(), request.getNewPassword());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpReq) {
		String ip = getClientIp(httpReq);
		return ResponseEntity.ok(authService.refreshAccessToken(request.getRefreshToken(), ip));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutBody body,
			@RequestHeader("Authorization") String authHeader) {
		// Revoke refresh token if provided
		if (body != null && body.getRefreshToken() != null) {
			authService.revokeRefreshToken(body.getRefreshToken());
		}
		// Blocklist access token
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			String jwt = authHeader.substring(7);
			try {
				String jti = jwtUtility.extractJti(jwt);
				long expiresAt = jwtUtility.extractExpiration(jwt).getTime();
				if (jti != null) {
					tokenBlocklistRepository.save(
							new TokenBlocklist(jti, expiresAt, System.currentTimeMillis()));
					tokenRevocationCache.revoke(jti, expiresAt);
				}
			} catch (Exception e) {
				log.warn("Logout: could not extract JTI from token ({}), skipping blocklist entry", e.getClass().getSimpleName());
			}
		}
		return ResponseEntity.ok().build();
	}

	@PostMapping("/reset-password/request")
	public ResponseEntity<Void> requestResetPasswordOtp(@Valid @RequestBody PasswordResetOtpRequest request, HttpServletRequest httpRequest) {
		String ip = getClientIp(httpRequest);
		if (!isOtpAllowed(request.getPhoneNumber()) || !isOtpAllowed("ip:" + ip)) {
			log.warn("OTP rate limit exceeded for reset phone={}*** ip={}", request.getPhoneNumber().substring(0, 3), ip);
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		authService.requestPasswordResetOtp(request.getPhoneNumber());
		return ResponseEntity.ok().build();
	}

	// ─── Web Admin Forgot Password Flow ──────────────────────────────────────────

	@PostMapping("/forgot-password/request-otp")
	public ResponseEntity<?> forgotPasswordRequestOtp(@Valid @RequestBody RequestOtpRequest request, HttpServletRequest httpRequest) {
		String ip = getClientIp(httpRequest);
		if (!isOtpAllowed(request.phone())) {
			log.warn("OTP rate limit exceeded for forgot-password phone={}*** ip={}", request.phone().substring(0, 3), ip);
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		try {
			webAdminPasswordResetService.requestOtp(request.phone());
			return ResponseEntity.ok().build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.ok().build();
		}
	}

	@PostMapping("/forgot-password/verify-otp")
	public ResponseEntity<?> forgotPasswordVerifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
		try {
			String tempToken = webAdminPasswordResetService.verifyOtp(request.phone(), request.otp());
			return ResponseEntity.ok(new VerifyOtpResponse(tempToken));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(java.util.Map.of("error", "INVALID_OTP", "message", "Invalid or expired OTP"));
		}
	}

	@PostMapping("/forgot-password/reset-password")
	public ResponseEntity<?> forgotPasswordResetPassword(
			@Valid @RequestBody com.khanabook.saas.webadmin.dto.ResetPasswordRequest request) {
		try {
			webAdminPasswordResetService.resetPassword(request.tempToken(), request.newPassword());
			return ResponseEntity.ok().build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(java.util.Map.of("error", "INVALID_TOKEN", "message", e.getMessage()));
		}
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class SignupOtpRequest {
		@NotBlank(message = "Phone number is required")
		@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
		private String phoneNumber;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ResetPasswordRequest {
		@NotBlank(message = "Phone number is required")
		@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
		private String phoneNumber;

		@NotBlank(message = "New password is required")
		@Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
		private String newPassword;

		@NotBlank(message = "OTP is required")
		@Size(min = 6, max = 6)
		private String otp;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class LoginRequest {
		@NotBlank(message = "Login ID is required")
		@Pattern(
				regexp = "^(\\d{10}|[^\\s@]+@[^\\s@]+\\.[^\\s@]+)$",
				message = "Login ID must be a 10-digit phone number or a valid email"
		)
		@Size(min = 10, max = 254)
		@JsonAlias({ "phoneNumber", "email" })
		private String loginId;

		@NotBlank(message = "Password is required")
			@Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
			private String password;

			@Size(max = 128)
			private String deviceId;
		}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class GoogleLoginRequest {
			@NotBlank(message = "idToken is required")
			private String idToken;

			@Size(max = 128)
			@Size(max = 128)
			private String deviceId;
		}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class PasswordResetOtpRequest {
		@NotBlank(message = "Phone number is required")
		@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
		private String phoneNumber;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class SignupRequest {
		@NotBlank(message = "Phone number is required")
		@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
		@Size(min = 10, max = 10)
		@JsonAlias("email")
		private String phoneNumber;

		@NotBlank(message = "Name is required")
		@Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
		private String name;

		@NotBlank(message = "Password is required")
		@Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
		private String password;

			@NotBlank(message = "OTP is required")
			@Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
			private String otp;

			@NotBlank(message = "Device ID is required")
			@Size(max = 128)
			private String deviceId;
		}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class RefreshRequest {
		@NotBlank(message = "Refresh token is required")
		private String refreshToken;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class LogoutBody {
		private String refreshToken;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class AuthResponse {
		private String token;
		private String refreshToken;
		private long expiresIn;
		@com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
		private Long restaurantId;
		private String userName;
		private String loginId;
		private String userEmail;
		private String whatsappNumber;
		private String role;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ChangePasswordRequest {
		@NotBlank(message = "Current password is required")
		private String currentPassword;

		@NotBlank(message = "New password is required")
		@Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
		private String newPassword;
	}
}
