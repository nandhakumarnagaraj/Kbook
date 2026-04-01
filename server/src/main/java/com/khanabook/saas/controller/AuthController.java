package com.khanabook.saas.controller;

import com.khanabook.saas.debug.DebugNDJSONLogger;
import com.khanabook.saas.entity.TokenBlocklist;
import com.khanabook.saas.repository.TokenBlocklistRepository;
import com.khanabook.saas.service.AuthService;
import com.khanabook.saas.utility.JwtUtility;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final JwtUtility jwtUtility;
	private final TokenBlocklistRepository tokenBlocklistRepository;

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		DebugNDJSONLogger.log(
				"pre-debug",
				"H2_SILENT_REAUTH_VIA_AUTH_ENDPOINT",
				"AuthController:login",
				"Auth login endpoint called",
				java.util.Map.of(
						"deviceIdPresent", request.getDeviceId() != null,
						"phoneNumberProvided", request.getPhoneNumber() != null,
						"phoneNumberLen", request.getPhoneNumber() == null ? -1 : request.getPhoneNumber().length()
				)
		);
		return ResponseEntity.ok(authService.login(request));
	}

	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
		DebugNDJSONLogger.log(
				"pre-debug",
				"H2_SILENT_REAUTH_VIA_AUTH_ENDPOINT",
				"AuthController:signup",
				"Auth signup endpoint called",
				java.util.Map.of(
						"deviceIdPresent", request.getDeviceId() != null,
						"phoneNumberProvided", request.getPhoneNumber() != null,
						"phoneNumberLen", request.getPhoneNumber() == null ? -1 : request.getPhoneNumber().length()
				)
		);
		return ResponseEntity.ok(authService.signup(request));
	}

	@PostMapping("/signup/request")
	public ResponseEntity<Void> requestSignupOtp(@Valid @RequestBody SignupOtpRequest request) {
		authService.requestSignupOtp(request.getPhoneNumber());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/google")
	public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
		DebugNDJSONLogger.log(
				"pre-debug",
				"H2_SILENT_REAUTH_VIA_AUTH_ENDPOINT",
				"AuthController:google",
				"Auth google endpoint called",
				java.util.Map.of(
						"deviceIdPresent", request.getDeviceId() != null
				)
		);
		return ResponseEntity.ok(authService.googleLogin(request));
	}

	@GetMapping("/check-user")
	public ResponseEntity<Boolean> checkUser(@RequestParam String phoneNumber) {
		if (phoneNumber == null || !phoneNumber.matches("^\\d{10}$")) {
			return ResponseEntity.ok(false);
		}
		DebugNDJSONLogger.log(
				"pre-debug",
				"H2_SILENT_REAUTH_VIA_AUTH_ENDPOINT",
				"AuthController:check-user",
				"Auth check-user endpoint called",
				java.util.Map.of(
						"phoneNumberLen", phoneNumber == null ? -1 : phoneNumber.length()
				)
		);
		return ResponseEntity.ok(authService.checkUserExists(phoneNumber));
	}

	@PostMapping("/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		DebugNDJSONLogger.log(
				"pre-debug",
				"H2_SILENT_REAUTH_VIA_AUTH_ENDPOINT",
				"AuthController:reset-password",
				"Auth reset-password endpoint called",
				java.util.Map.of(
						"phoneNumberLen", request.getPhoneNumber() == null ? -1 : request.getPhoneNumber().length()
				)
		);
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
				}
			} catch (Exception ignored) {
				// Token may already be expired — logout is still successful
			}
		}
		return ResponseEntity.ok().build();
	}

	@PostMapping("/reset-password/request")
	public ResponseEntity<Void> requestResetPasswordOtp(@Valid @RequestBody PasswordResetOtpRequest request) {
		DebugNDJSONLogger.log(
				"pre-debug",
				"H2_SILENT_REAUTH_VIA_AUTH_ENDPOINT",
				"AuthController:reset-password-request",
				"Auth reset-password request endpoint called",
				java.util.Map.of(
						"phoneNumberLen", request.getPhoneNumber() == null ? -1 : request.getPhoneNumber().length()
				)
		);
		authService.requestPasswordResetOtp(request.getPhoneNumber());
		return ResponseEntity.ok().build();
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class PasswordResetOtpRequest {
		@NotBlank(message = "Phone number is required")
		@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
		@Size(min = 10, max = 10)
		private String phoneNumber;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class SignupOtpRequest {
		@NotBlank(message = "Phone number is required")
		@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
		@Size(min = 10, max = 10)
		private String phoneNumber;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ResetPasswordRequest {
		@NotBlank(message = "Phone number is required")
		@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
		@Size(min = 10, max = 10)
		private String phoneNumber;
		@NotBlank(message = "OTP is required")
		@Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
		private String otp;
		@NotBlank(message = "New password is required")
		@Size(min = 6, max = 128)
		private String newPassword;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class LoginRequest {
		@NotBlank(message = "Phone number is required")
		@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
		@Size(min = 10, max = 10)
		@JsonAlias("email")
		private String phoneNumber;

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
		private String deviceId;
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
