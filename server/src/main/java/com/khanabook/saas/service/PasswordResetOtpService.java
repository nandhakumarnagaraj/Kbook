package com.khanabook.saas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khanabook.saas.entity.OtpRequest;
import com.khanabook.saas.repository.OtpRequestRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Service
public class PasswordResetOtpService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetOtpService.class);
	private static final long OTP_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
	private static final int MAX_ATTEMPTS = 5;
	private static final String SIGNUP_PREFIX = "signup:";
	private static final String PASSWORD_RESET_PREFIX = "password-reset:";
	private static final String MOBILE_UPDATE_PREFIX = "mobile-update:";

	private final OtpRequestRepository otpRequestRepository;
	private final PasswordEncoder passwordEncoder;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public PasswordResetOtpService(OtpRequestRepository otpRequestRepository, PasswordEncoder passwordEncoder) {
		this.otpRequestRepository = otpRequestRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Value("${whatsapp.meta.access-token:}")
	private String metaAccessToken;

	@Value("${whatsapp.meta.phone-number-id:}")
	private String phoneNumberId;

	@Value("${whatsapp.meta.otp-template-name:}")
	private String otpTemplateName;

	@Value("${whatsapp.meta.fixed-otp:}")
	private String fixedOtp;

	@Transactional
	public void issueOtp(String phoneNumber) {
		issueOtp(PASSWORD_RESET_PREFIX + phoneNumber, phoneNumber);
	}

	@Transactional
	public void issueSignupOtp(String phoneNumber) {
		issueOtp(SIGNUP_PREFIX + phoneNumber, phoneNumber);
	}

	@Transactional
	public void validateSignupOtpOrThrow(String phoneNumber, String otp) {
		validateOtpOrThrow(SIGNUP_PREFIX + phoneNumber, phoneNumber, otp);
	}

	@Transactional
	public void validateOtpOrThrow(String phoneNumber, String otp) {
		validateOtpOrThrow(PASSWORD_RESET_PREFIX + phoneNumber, phoneNumber, otp);
	}

	@Transactional
	public void issueMobileUpdateOtp(Long tenantId, String phoneNumber) {
		issueOtp(MOBILE_UPDATE_PREFIX + tenantId, phoneNumber);
	}

	@Transactional
	public void validateMobileUpdateOtpOrThrow(Long tenantId, String phoneNumber, String otp) {
		validateOtpOrThrow(MOBILE_UPDATE_PREFIX + tenantId, phoneNumber, otp);
	}

	private void issueOtp(String challengeKey, String phoneNumber) {
		String otp = (fixedOtp != null && fixedOtp.matches("^\\d{6}$"))
				? fixedOtp
				: String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1_000_000));

		long now = System.currentTimeMillis();
		OtpRequest request = otpRequestRepository.findByChallengeKey(challengeKey)
				.orElseGet(() -> OtpRequest.builder().challengeKey(challengeKey).build());

		request.setPhoneNumber(phoneNumber);
		request.setOtp(passwordEncoder.encode(otp));
		request.setExpiresAt(now + OTP_TTL_MILLIS);
		request.setAttempts(0);
		request.setCreatedAt(now);

		otpRequestRepository.save(request);
		sendOtp(challengeKey, phoneNumber, otp);
	}

	private void validateOtpOrThrow(String challengeKey, String phoneNumber, String otp) {
		OtpRequest challenge = otpRequestRepository.findByChallengeKey(challengeKey).orElse(null);

		if (challenge == null || challenge.getExpiresAt() < System.currentTimeMillis()) {
			if (challenge != null) {
				otpRequestRepository.delete(challenge);
			}
			throw new IllegalArgumentException("OTP expired. Please request a new code.");
		}

		if (challenge.getAttempts() >= MAX_ATTEMPTS) {
			otpRequestRepository.delete(challenge);
			throw new IllegalArgumentException("Too many invalid OTP attempts. Please request a new code.");
		}

		if (!challenge.getPhoneNumber().equals(phoneNumber)) {
			otpRequestRepository.delete(challenge);
			throw new IllegalArgumentException("OTP requested for a different mobile number. Please request a new code.");
		}

		if (!passwordEncoder.matches(otp, challenge.getOtp())) {
			challenge.setAttempts(challenge.getAttempts() + 1);
			otpRequestRepository.save(challenge);
			throw new IllegalArgumentException("Invalid OTP.");
		}

		otpRequestRepository.delete(challenge);
	}

	@Scheduled(fixedDelay = 3600000) // Every hour
	@Transactional
	public void cleanupExpiredOtps() {
		long now = System.currentTimeMillis();
		otpRequestRepository.deleteByExpiresAtBefore(now);
	}

	private void sendOtp(String challengeKey, String phoneNumber, String otp) {
		if (metaAccessToken == null || metaAccessToken.isBlank()
				|| phoneNumberId == null || phoneNumberId.isBlank()
				|| otpTemplateName == null || otpTemplateName.isBlank()) {
			log.warn("WhatsApp OTP config missing for challengeType={} phone={}",
					describeChallenge(challengeKey), phoneNumber);
			return;
		}

		String formattedPhoneNumber = formatWhatsappPhoneNumber(phoneNumber);

		String body = """
					{
					  "messaging_product": "whatsapp",
					  "to": "%s",
				  "type": "template",
				  "template": {
				    "name": "%s",
				    "language": { "code": "en" },
				    "components": [
				      {
				        "type": "body",
				        "parameters": [
				          { "type": "text", "text": "%s" }
				        ]
				      },
				      {
				        "type": "button",
				        "sub_type": "url",
				        "index": "0",
				        "parameters": [
				          { "type": "text", "text": "%s" }
				        ]
				      }
				    ]
					  }
					}
					""".formatted(escapeJson(formattedPhoneNumber), escapeJson(otpTemplateName), escapeJson(otp), escapeJson(otp));

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://graph.facebook.com/v17.0/" + phoneNumberId + "/messages"))
				.timeout(Duration.ofSeconds(30))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + metaAccessToken)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 300) {
				log.error(
						"WhatsApp OTP send failed for challengeType={} phone={} formattedPhone={} status={} body={}",
						describeChallenge(challengeKey),
						phoneNumber,
						formattedPhoneNumber,
						response.statusCode(),
						response.body());
				throw new IllegalStateException("WhatsApp OTP send failed with status " + response.statusCode());
			}
			log.info("WhatsApp API Response (SUCCESS) for {} phone={}: {}", 
					describeChallenge(challengeKey), formattedPhoneNumber, response.body());
			log.info("WhatsApp OTP sent for challengeType={} phone={} formattedPhone={}",
					describeChallenge(challengeKey), phoneNumber, formattedPhoneNumber);
		} catch (Exception e) {
			otpRequestRepository.deleteByChallengeKey(challengeKey);
			log.error("Failed to send OTP for challengeType={} phone={} formattedPhone={}",
					describeChallenge(challengeKey), phoneNumber, formattedPhoneNumber, e);
			throw new IllegalStateException("Failed to send OTP. Please try again.", e);
		}
	}

	private String formatWhatsappPhoneNumber(String phoneNumber) {
		String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9]", "");
		if (digits.length() == 10) {
			if (digits.startsWith("91")) {
				return digits; // 10-digit number already starting with 91
			}
			return "91" + digits;
		}
		if (digits.startsWith("91") && digits.length() == 12) {
			return digits;
		}
		return digits;
	}

	private String describeChallenge(String challengeKey) {
		if (challengeKey.startsWith(SIGNUP_PREFIX)) {
			return "signup";
		}
		if (challengeKey.startsWith(PASSWORD_RESET_PREFIX)) {
			return "password-reset";
		}
		if (challengeKey.startsWith(MOBILE_UPDATE_PREFIX)) {
			return "mobile-update";
		}
		return "unknown";
	}

	private String escapeJson(String input) {
		return input.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
