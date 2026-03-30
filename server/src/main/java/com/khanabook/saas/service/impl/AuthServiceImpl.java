package com.khanabook.saas.service.impl;

import com.khanabook.saas.controller.AuthController.AuthResponse;
import com.khanabook.saas.controller.AuthController.LoginRequest;
import com.khanabook.saas.controller.AuthController.SignupRequest;
import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.service.AuthService;
import com.khanabook.saas.utility.JwtUtility;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

	private final UserRepository userRepository;
	private final RestaurantProfileRepository restaurantProfileRepository;
	private final JwtUtility jwtUtility;
	private final PasswordEncoder passwordEncoder;
	private final com.khanabook.saas.service.PasswordResetOtpService passwordResetOtpService;

	@org.springframework.beans.factory.annotation.Value("${google.client.id}")
	private String googleClientId;

	@jakarta.annotation.PostConstruct
	public void validateConfig() {
		if (googleClientId == null || googleClientId.isBlank()) {
			throw new IllegalStateException(
					"google.client.id is not configured. Google Login will fail. Ensure GOOGLE_CLIENT_ID is set.");
		}
	}

	@Override
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByLoginId(request.getPhoneNumber())
				.or(() -> userRepository.findByEmail(request.getPhoneNumber()))
				.or(() -> userRepository.findByWhatsappNumber(request.getPhoneNumber()))
				.orElseThrow(() -> new IllegalArgumentException("Invalid phone number or password"));

		if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			throw new IllegalArgumentException("Invalid phone number or password");
		}

		if (!Boolean.TRUE.equals(user.getIsActive())) {
			throw new IllegalArgumentException("Account is disabled. Contact your administrator.");
		}

		backfillLoginIdIfMissing(user);

		log.info("User logged in: restaurantId={}", user.getRestaurantId());
		String token = jwtUtility.generateToken(getLoginIdentifier(user), user.getRestaurantId(), user.getRole().name());
			return new AuthResponse(token, user.getRestaurantId(), user.getName(), getLoginIdentifier(user),
					user.getEmail(), user.getWhatsappNumber(), user.getRole().name());
	}

	@Override
	@Transactional
	public AuthResponse signup(SignupRequest request) {
		ensurePhoneNumberAvailableForSignup(request.getPhoneNumber());
		passwordResetOtpService.validateSignupOtpOrThrow(request.getPhoneNumber(), request.getOtp());

		Long newRestaurantId = Math.abs(UUID.randomUUID().getMostSignificantBits());

		RestaurantProfile profile = new RestaurantProfile();
		profile.setRestaurantId(newRestaurantId);
		profile.setDeviceId(request.getDeviceId());
		profile.setLocalId(1L);
		profile.setShopName(request.getName() + "'s Restaurant");
		profile.setUpdatedAt(System.currentTimeMillis());
		profile.setServerUpdatedAt(System.currentTimeMillis());
		profile.setCreatedAt(System.currentTimeMillis());
		restaurantProfileRepository.save(profile);

		String hashedPassword = passwordEncoder.encode(request.getPassword());

		User user = new User();
		user.setName(request.getName());
		user.setEmail(request.getPhoneNumber());
		user.setLoginId(request.getPhoneNumber());
		user.setAuthProvider(AuthProvider.PHONE);
		user.setWhatsappNumber(request.getPhoneNumber());
		user.setPasswordHash(hashedPassword);
		user.setRestaurantId(newRestaurantId);
		user.setDeviceId(request.getDeviceId());
		user.setLocalId(1L);
		user.setRole(UserRole.OWNER); // Strictly force OWNER on signup
		user.setIsActive(true);
		user.setUpdatedAt(System.currentTimeMillis());
		user.setServerUpdatedAt(System.currentTimeMillis());
		user.setCreatedAt(System.currentTimeMillis());
		userRepository.save(user);

		log.info("New user signed up: restaurantId={}", newRestaurantId);
		String token = jwtUtility.generateToken(getLoginIdentifier(user), newRestaurantId, user.getRole().name());
			return new AuthResponse(token, newRestaurantId, user.getName(), getLoginIdentifier(user), user.getEmail(),
							user.getWhatsappNumber(), user.getRole().name());
	}

	@Override
	@Transactional
	public void requestSignupOtp(String phoneNumber) {
		ensurePhoneNumberAvailableForSignup(phoneNumber);
		passwordResetOtpService.issueSignupOtp(phoneNumber);
	}

	@Override
	@Transactional
	public AuthResponse googleLogin(com.khanabook.saas.controller.AuthController.GoogleLoginRequest request) {
		try {
			com.google.api.client.http.HttpTransport transport = new com.google.api.client.http.javanet.NetHttpTransport();
			com.google.api.client.json.JsonFactory jsonFactory = new com.google.api.client.json.gson.GsonFactory();
			com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier verifier = new com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(
					transport, jsonFactory).setAudience(java.util.Collections.singletonList(googleClientId)).build();

			com.google.api.client.googleapis.auth.oauth2.GoogleIdToken idToken = verifier.verify(request.getIdToken());
			if (idToken != null) {
				com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload = idToken.getPayload();
				String email = payload.getEmail();
				String name = (String) payload.get("name");

				// 1. Try finding by primary identifier (phone/email) OR by linked google email
				return userRepository.findByLoginId(email)
						.or(() -> userRepository.findByEmail(email))
						.or(() -> userRepository.findByGoogleEmail(email))
						.map(user -> {
							if (!Boolean.TRUE.equals(user.getIsActive())) {
								throw new IllegalArgumentException("Account is disabled. Contact your administrator.");
							}
							// Upgrade older records to explicit Google ownership when linked.
							if (user.getGoogleEmail() == null) {
								user.setGoogleEmail(email);
							}
							if (user.getLoginId() == null || user.getLoginId().isBlank()) {
								user.setLoginId(email);
							}
							user.setAuthProvider(AuthProvider.GOOGLE);
							user.setUpdatedAt(System.currentTimeMillis());
							userRepository.save(user);
							String token = jwtUtility.generateToken(getLoginIdentifier(user), user.getRestaurantId(),
									user.getRole().name());
								return new AuthResponse(token, user.getRestaurantId(), user.getName(), getLoginIdentifier(user),
										user.getEmail(), user.getWhatsappNumber(), user.getRole().name());
						}).orElseGet(() -> {

					Long newRestaurantId = Math.abs(UUID.randomUUID().getMostSignificantBits());

					RestaurantProfile profile = new RestaurantProfile();
					profile.setRestaurantId(newRestaurantId);
					profile.setDeviceId(request.getDeviceId());
					profile.setLocalId(1L);
					profile.setShopName((name != null ? name : "User") + "'s Restaurant");
					profile.setUpdatedAt(System.currentTimeMillis());
					profile.setServerUpdatedAt(System.currentTimeMillis());
					profile.setCreatedAt(System.currentTimeMillis());
					restaurantProfileRepository.save(profile);

						User user = new User();
						user.setName(name != null ? name : "Google User");
						user.setEmail(email);
						user.setLoginId(email);
						user.setGoogleEmail(email);
						user.setAuthProvider(AuthProvider.GOOGLE);
						user.setWhatsappNumber(null);
					user.setPasswordHash("GOOGLE_AUTH");
					user.setRestaurantId(newRestaurantId);
					user.setDeviceId(request.getDeviceId());
					user.setLocalId(1L);
					user.setRole(UserRole.OWNER);
					user.setIsActive(true);
					user.setUpdatedAt(System.currentTimeMillis());
					user.setServerUpdatedAt(System.currentTimeMillis());
					user.setCreatedAt(System.currentTimeMillis());
					userRepository.save(user);

						String token = jwtUtility.generateToken(getLoginIdentifier(user), newRestaurantId, user.getRole().name());
							return new AuthResponse(token, newRestaurantId, user.getName(), getLoginIdentifier(user),
									user.getEmail(), user.getWhatsappNumber(), user.getRole().name());
					});
			} else {
				throw new IllegalArgumentException("Invalid Google ID token.");
			}
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			log.error("Google login failed", e);
			throw new IllegalArgumentException(
					"Google login failed. Please ensure you are using a valid Google account.");
		}
	}

	@Override
	@Transactional
	public void requestPasswordResetOtp(String phoneNumber) {
		findUserByLoginId(phoneNumber)
				.orElseThrow(() -> new IllegalArgumentException("No account found with this number"));
		passwordResetOtpService.issueOtp(phoneNumber);
	}

	@Override
	@Transactional
	public void resetPassword(String phoneNumber, String otp, String newPassword) {
		passwordResetOtpService.validateOtpOrThrow(phoneNumber, otp);

		User user = findUserByLoginId(phoneNumber)
				.orElseThrow(() -> new IllegalArgumentException("User not found"));

		user.setPasswordHash(passwordEncoder.encode(newPassword));
		user.setUpdatedAt(System.currentTimeMillis());
		user.setServerUpdatedAt(System.currentTimeMillis());
		userRepository.save(user);
		log.info("Password changed successful for user: {}", phoneNumber);
	}

	@Override
	public boolean checkUserExists(String phoneNumber) {
		return findUserByLoginId(phoneNumber).isPresent();
	}

	private java.util.Optional<User> findUserByLoginId(String phoneNumber) {
		return userRepository.findByLoginId(phoneNumber)
				.or(() -> userRepository.findByEmail(phoneNumber))
				.or(() -> userRepository.findByWhatsappNumber(phoneNumber));
	}

	private void ensurePhoneNumberAvailableForSignup(String phoneNumber) {
		if (findUserByLoginId(phoneNumber).isPresent()) {
			throw new IllegalArgumentException("This number is already registered.");
		}
	}

	private void backfillLoginIdIfMissing(User user) {
		if (user.getLoginId() != null && !user.getLoginId().isBlank()) {
			return;
		}

		if (user.getAuthProvider() == AuthProvider.GOOGLE && user.getGoogleEmail() != null
				&& !user.getGoogleEmail().isBlank()) {
			user.setLoginId(user.getGoogleEmail());
		} else {
			user.setLoginId(user.getEmail());
		}
		user.setUpdatedAt(System.currentTimeMillis());
		userRepository.save(user);
	}

	private String getLoginIdentifier(User user) {
		if (user.getLoginId() != null && !user.getLoginId().isBlank()) {
			return user.getLoginId();
		}
		return user.getEmail();
	}
}
