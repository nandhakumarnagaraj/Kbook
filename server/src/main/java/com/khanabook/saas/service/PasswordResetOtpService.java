package com.khanabook.saas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetOtpService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetOtpService.class);
	private static final long OTP_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
	private static final int MAX_ATTEMPTS = 5;
	private static final String SIGNUP_PREFIX = "signup:";
	private static final String PASSWORD_RESET_PREFIX = "password-reset:";
	private static final String MOBILE_UPDATE_PREFIX = "mobile-update:";

	private final Map<String, OtpChallenge> challenges = new ConcurrentHashMap<>();
	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Value("${whatsapp.meta.access-token:}")
	private String metaAccessToken;

	@Value("${whatsapp.meta.phone-number-id:}")
	private String phoneNumberId;

	@Value("${whatsapp.meta.otp-template-name:}")
	private String otpTemplateName;

	@Value("${whatsapp.meta.fixed-otp:}")
	private String fixedOtp;

	public void issueOtp(String phoneNumber) {
		issueOtp(PASSWORD_RESET_PREFIX + phoneNumber, phoneNumber);
	}

	public void issueSignupOtp(String phoneNumber) {
		issueOtp(SIGNUP_PREFIX + phoneNumber, phoneNumber);
	}

	public void validateSignupOtpOrThrow(String phoneNumber, String otp) {
		validateOtpOrThrow(SIGNUP_PREFIX + phoneNumber, phoneNumber, otp);
	}

	public void validateOtpOrThrow(String phoneNumber, String otp) {
		validateOtpOrThrow(PASSWORD_RESET_PREFIX + phoneNumber, phoneNumber, otp);
	}

	public void issueMobileUpdateOtp(Long tenantId, String phoneNumber) {
		issueOtp(MOBILE_UPDATE_PREFIX + tenantId, phoneNumber);
	}

	public void validateMobileUpdateOtpOrThrow(Long tenantId, String phoneNumber, String otp) {
		validateOtpOrThrow(MOBILE_UPDATE_PREFIX + tenantId, phoneNumber, otp);
	}

	private void issueOtp(String challengeKey, String phoneNumber) {
		String otp = (fixedOtp != null && fixedOtp.matches("^\\d{6}$"))
				? fixedOtp
				: String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1_000_000));
		challenges.put(challengeKey, new OtpChallenge(otp, System.currentTimeMillis() + OTP_TTL_MILLIS, 0, phoneNumber));
		sendOtp(challengeKey, phoneNumber, otp);
	}

	private void validateOtpOrThrow(String challengeKey, String phoneNumber, String otp) {
		OtpChallenge challenge = challenges.get(challengeKey);
		if (challenge == null || challenge.expiresAtMillis() < System.currentTimeMillis()) {
			challenges.remove(challengeKey);
			throw new IllegalArgumentException("OTP expired. Please request a new code.");
		}

		if (challenge.attempts() >= MAX_ATTEMPTS) {
			challenges.remove(challengeKey);
			throw new IllegalArgumentException("Too many invalid OTP attempts. Please request a new code.");
		}

		if (!challenge.phoneNumber().equals(phoneNumber)) {
			challenges.remove(challengeKey);
			throw new IllegalArgumentException("OTP requested for a different mobile number. Please request a new code.");
		}

		if (!challenge.otp().equals(otp)) {
			challenges.put(challengeKey, challenge.withAttempts(challenge.attempts() + 1));
			throw new IllegalArgumentException("Invalid OTP.");
		}

		challenges.remove(challengeKey);
	}

	private void sendOtp(String challengeKey, String phoneNumber, String otp) {
		if (metaAccessToken == null || metaAccessToken.isBlank()
				|| phoneNumberId == null || phoneNumberId.isBlank()
				|| otpTemplateName == null || otpTemplateName.isBlank()) {
			log.warn("WhatsApp OTP config missing. Password reset OTP for {} is {}", phoneNumber, otp);
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
					throw new IllegalStateException("WhatsApp OTP send failed with status " + response.statusCode());
				}
			} catch (Exception e) {
			challenges.remove(challengeKey);
			throw new IllegalStateException("Failed to send OTP. Please try again.", e);
		}
	}

	private String formatWhatsappPhoneNumber(String phoneNumber) {
		return "91" + phoneNumber;
	}

	private String escapeJson(String input) {
		return input.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private record OtpChallenge(String otp, long expiresAtMillis, int attempts, String phoneNumber) {
		private OtpChallenge withAttempts(int nextAttempts) {
			return new OtpChallenge(otp, expiresAtMillis, nextAttempts, phoneNumber);
		}
	}
}
