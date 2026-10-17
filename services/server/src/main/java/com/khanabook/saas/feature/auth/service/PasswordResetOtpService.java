package com.khanabook.saas.feature.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khanabook.saas.feature.auth.entity.OtpRequest;
import com.khanabook.saas.feature.auth.repository.OtpRequestRepository;
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
	private static final String STAFF_INVITE_PREFIX = "staff-invite:";
	private static final String WEB_PASSWORD_RESET_PREFIX = "web-password-reset:";
	private static final String MOBILE_UPDATE_PREFIX = "mobile-update:";

	/** Legacy single-template fallback. Kept so an unconfigured operation degrades
	 *  to today's behaviour instead of failing. */
	private static final String LEGACY_TEMPLATE_NAME = "verify_code";

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

	/** Legacy single-template name. Kept as the fallback when a specific
	 *  per-operation template is not configured. */
	@Value("${whatsapp.meta.otp-template-name:}")
	private String legacyOtpTemplateName;

	@Value("${whatsapp.meta.otp-template-signup:}")
	private String signupOtpTemplateName;

	@Value("${whatsapp.meta.otp-template-password-reset:}")
	private String passwordResetOtpTemplateName;

	@Value("${whatsapp.meta.otp-template-staff-invite:}")
	private String staffInviteOtpTemplateName;

	/**
	 * Template used to deliver a GENERATED staff password. Falls back to the
	 * staff-invite template when unset so an existing configuration keeps working,
	 * but a dedicated template should be approved: Meta template wording is fixed
	 * at approval time, so an "your OTP is {{1}}" template would label a password
	 * as a one-time code and confuse the recipient.
	 */
	@Value("${whatsapp.meta.staff-credentials-template:}")
	private String staffCredentialsTemplateName;

	@Value("${whatsapp.meta.otp-template-mobile-update:}")
	private String mobileUpdateOtpTemplateName;

	@Value("${whatsapp.meta.otp-template-web-reset:}")
	private String webResetOtpTemplateName;

	@Transactional
	public void issueOtp(String phoneNumber) {
		issueOtp(PASSWORD_RESET_PREFIX + phoneNumber, phoneNumber, 6);
	}

	@Transactional
	public void issueSignupOtp(String phoneNumber) {
		issueOtp(SIGNUP_PREFIX + phoneNumber, phoneNumber, 6);
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
		issueOtp(MOBILE_UPDATE_PREFIX + tenantId, phoneNumber, 6);
	}

	@Transactional
	public void validateMobileUpdateOtpOrThrow(Long tenantId, String phoneNumber, String otp) {
		validateOtpOrThrow(MOBILE_UPDATE_PREFIX + tenantId, phoneNumber, otp);
	}

	@Transactional
	public void issueWebAdminOtp(String phoneNumber) {
		issueOtp(WEB_PASSWORD_RESET_PREFIX + phoneNumber, phoneNumber, 6);
	}

	@Transactional
	public void validateWebAdminOtpOrThrow(String phoneNumber, String otp) {
		validateOtpOrThrow(WEB_PASSWORD_RESET_PREFIX + phoneNumber, phoneNumber, otp);
	}

	// NOTE: the former issueStaffInviteOtp() was removed. It minted a challenge
	// under STAFF_INVITE_PREFIX that no validator and no endpoint ever consumed, so
	// the code it sent could not be redeemed — staff could only recover by triggering
	// a second code in the password-reset namespace. Onboarding now delivers a
	// generated password via sendStaffCredentials(). The prefix and the template
	// lookups below are retained so any pre-existing staff-invite rows still
	// resolve to the right template and audit description.

	private void issueOtp(String challengeKey, String phoneNumber, int digits) {
		// Cooldown: reject if last OTP was issued less than 60 seconds ago
		OtpRequest existing = otpRequestRepository.findByChallengeKey(challengeKey).orElse(null);
		if (existing != null && existing.getCreatedAt() != null
				&& existing.getCreatedAt() > System.currentTimeMillis() - 60_000L) {
			throw new IllegalArgumentException("Please wait 60 seconds before requesting a new code.");
		}

		int upperBound = digits == 4 ? 10_000 : 1_000_000;
		String format = digits == 4 ? "%04d" : "%06d";
		String otp = String.format(format,
				java.util.concurrent.ThreadLocalRandom.current().nextInt(0, upperBound));

		long now = System.currentTimeMillis();
		OtpRequest request = existing != null ? existing
				: OtpRequest.builder().challengeKey(challengeKey).build();

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

	private boolean isWhatsappConfigured(String templateName) {
		return metaAccessToken != null && !metaAccessToken.isBlank()
				&& phoneNumberId != null && !phoneNumberId.isBlank()
				&& templateName != null && !templateName.isBlank();
	}

	private String maskPhone(String phoneNumber) {
		if (phoneNumber == null || phoneNumber.length() < 3) return "***";
		return phoneNumber.substring(0, 3) + "***";
	}

	/**
	 * Delivers a GENERATED staff password over WhatsApp.
	 *
	 * <p>Replaces the previous OTP-only onboarding. The staff-invite challenge
	 * written by the now-removed {@code issueStaffInviteOtp} had no validator and no
	 * consuming endpoint, so that code could never be redeemed; the staff member was
	 * expected to fall back to Forgot Password, which issues a second code in a
	 * different namespace. A generated password removes the dead challenge entirely.
	 *
	 * <p>Deliberately does NOT log the password. The OTP path logs its code because
	 * it is single-use and short-lived; a password is a durable secret.
	 */
	@Transactional
	public void sendStaffCredentials(String phoneNumber, String password) {
		String templateName = (staffCredentialsTemplateName != null && !staffCredentialsTemplateName.isBlank())
				? staffCredentialsTemplateName
				: staffInviteOtpTemplateName;
		if (!isWhatsappConfigured(templateName)) {
			log.warn("WhatsApp staff-credentials config missing for phone={}. Password was NOT sent. "
					+ "The owner can re-send it from the staff list.", maskPhone(phoneNumber));
			return;
		}
		sendTemplateMessage(phoneNumber, templateName, password, "staff-credentials");
	}

private void sendOtp(String challengeKey, String phoneNumber, String otp) {
		String templateName = templateFor(challengeKey);
		if (!isWhatsappConfigured(templateName)) {
			log.warn("WhatsApp OTP config missing for challengeType={} phone={}. Generated OTP for local testing: {}",
					describeChallenge(challengeKey), phoneNumber, otp);
			return;
		}

		try {
			sendTemplateMessage(phoneNumber, templateName, otp, describeChallenge(challengeKey));
		} catch (RuntimeException e) {
			otpRequestRepository.deleteByChallengeKey(challengeKey);
			throw new IllegalStateException("Failed to send OTP. Please try again.", e);
		}
	}

	/**
	 * Posts a single-parameter WhatsApp template. The parameter is supplied twice
	 * because the OTP templates bind it to both the message body and the copy-code
	 * button.
	 */
	private void sendTemplateMessage(String phoneNumber, String templateName, String param, String label) {
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
				""".formatted(escapeJson(formattedPhoneNumber), escapeJson(templateName), escapeJson(param), escapeJson(param));

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
						"WhatsApp send failed for type={} phone={} formattedPhone={} status={}",
						label,
						phoneNumber,
						formattedPhoneNumber,
						response.statusCode());
				throw new IllegalStateException("WhatsApp send failed with status " + response.statusCode());
			}
			log.info("WhatsApp template sent for type={} phone={} formattedPhone={} template={}",
					label, phoneNumber, formattedPhoneNumber, templateName);
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			log.error("Failed to send WhatsApp message for type={} phone={} formattedPhone={}",
					label, phoneNumber, formattedPhoneNumber, e);
			throw new IllegalStateException("Failed to send message. Please try again.", e);
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
		if (challengeKey == null) return "unknown";
		if (challengeKey.startsWith(SIGNUP_PREFIX)) {
			return "signup";
		}
		if (challengeKey.startsWith(STAFF_INVITE_PREFIX)) {
			return "staff-invite";
		}
		if (challengeKey.startsWith(PASSWORD_RESET_PREFIX)) {
			return "password-reset";
		}
		if (challengeKey.startsWith(WEB_PASSWORD_RESET_PREFIX)) {
			return "web-password-reset";
		}
		if (challengeKey.startsWith(MOBILE_UPDATE_PREFIX)) {
			return "mobile-update";
		}
		return "unknown";
	}

	/**
	 * Resolves the WhatsApp template name for a challenge, keyed on the
	 * challengeKey prefix. Falls back to the legacy single-template name when a
	 * specific per-operation template is not configured, and to null when the
	 * legacy name is also blank — in which case sendOtp keeps the existing
	 * local-testing log path and does NOT throw.
	 */
	private String templateFor(String challengeKey) {
		if (challengeKey == null) return legacyOtpTemplateName;
		String specific;
		if (challengeKey.startsWith(SIGNUP_PREFIX)) {
			specific = signupOtpTemplateName;
		} else if (challengeKey.startsWith(STAFF_INVITE_PREFIX)) {
			specific = staffInviteOtpTemplateName;
		} else if (challengeKey.startsWith(WEB_PASSWORD_RESET_PREFIX)) {
			specific = webResetOtpTemplateName;
		} else if (challengeKey.startsWith(MOBILE_UPDATE_PREFIX)) {
			specific = mobileUpdateOtpTemplateName;
		} else {
			specific = passwordResetOtpTemplateName;
		}
		return (specific != null && !specific.isBlank())
				? specific
				: legacyOtpTemplateName;
	}

	private String escapeJson(String input) {
		return input.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
