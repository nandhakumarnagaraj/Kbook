package com.khanabook.saas.controller;

import com.khanabook.saas.entity.TokenBlocklist;
import com.khanabook.saas.repository.TokenBlocklistRepository;
import com.khanabook.saas.security.TokenRevocationCache;
import com.khanabook.saas.service.AuthService;
import com.khanabook.saas.service.LoginRateLimiter;
import com.khanabook.saas.service.OtpRateLimiter;
import com.khanabook.saas.service.WebAdminPasswordResetService;
import com.khanabook.saas.utility.JwtUtility;
import com.khanabook.saas.webadmin.dto.RequestOtpRequest;
import com.khanabook.saas.webadmin.dto.VerifyOtpRequest;
import com.khanabook.saas.webadmin.dto.VerifyOtpResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final AuthService authService;
	private final JwtUtility jwtUtility;
	private final TokenBlocklistRepository tokenBlocklistRepository;
	private final TokenRevocationCache tokenRevocationCache;
	private final OtpRateLimiter otpRateLimiter;
	private final LoginRateLimiter loginRateLimiter;
	private final WebAdminPasswordResetService webAdminPasswordResetService;

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		String ip = getClientIp(httpRequest);
		if (!loginRateLimiter.tryConsume(ip)) {
			log.warn("Login rate limit exceeded ip={}", ip);
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

	@PostMapping("/signup/request")
	public ResponseEntity<Void> requestSignupOtp(@Valid @RequestBody SignupOtpRequest request) {
		if (!otpRateLimiter.tryConsume(request.getPhoneNumber())) {
			log.warn("OTP rate limit exceeded for signup phone={}***", request.getPhoneNumber().substring(0, 3));
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		authService.requestSignupOtp(request.getPhoneNumber());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/google")
	public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
			HttpServletRequest httpRequest) {
		String ip = getClientIp(httpRequest);
		if (!loginRateLimiter.tryConsume(ip)) {
			log.warn("Google login rate limit exceeded ip={}", ip);
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		return ResponseEntity.ok(authService.googleLogin(request));
	}

	@GetMapping("/check-user")
	public ResponseEntity<Boolean> checkUser(@RequestParam String phoneNumber) {
		if (phoneNumber == null || !phoneNumber.matches("^\\d{10}$")) {
			return ResponseEntity.ok(false);
		}
		return ResponseEntity.ok(authService.checkUserExists(phoneNumber));
	}

	@PostMapping("/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request.getPhoneNumber(), request.getOtp(), request.getNewPassword());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
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
				// Token may already be expired — logout is still considered successful
				log.warn("Logout: could not extract JTI from token ({}), skipping blocklist entry", e.getClass().getSimpleName());
			}
		}
		return ResponseEntity.ok().build();
	}

	@PostMapping("/reset-password/request")
	public ResponseEntity<Void> requestResetPasswordOtp(@Valid @RequestBody PasswordResetOtpRequest request) {
		if (!otpRateLimiter.tryConsume(request.getPhoneNumber())) {
			log.warn("OTP rate limit exceeded for reset phone={}***", request.getPhoneNumber().substring(0, 3));
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		authService.requestPasswordResetOtp(request.getPhoneNumber());
		return ResponseEntity.ok().build();
	}

	// ─── Web Admin Forgot Password Flow ──────────────────────────────────────────

	@PostMapping("/forgot-password/request-otp")
	public ResponseEntity<?> forgotPasswordRequestOtp(@Valid @RequestBody RequestOtpRequest request, HttpServletRequest httpRequest) {
		String ip = getClientIp(httpRequest);
		if (!otpRateLimiter.tryConsume(request.phone())) {
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
	public static class AuthResponse {
		private String token;
		private Long restaurantId;
		private String userName;
		private String loginId;
		private String userEmail;
		private String whatsappNumber;
		private String role;
	}
}
