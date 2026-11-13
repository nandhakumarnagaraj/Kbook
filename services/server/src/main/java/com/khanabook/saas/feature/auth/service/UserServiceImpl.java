package com.khanabook.saas.feature.auth.service;

import com.khanabook.saas.core.utility.PhoneNormalizer;
import com.khanabook.saas.feature.auth.data.OwnerIdentityResponse;
import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.notifications.service.EmailNotificationService;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserRepository repository;
	private final RestaurantProfileRepository restaurantProfileRepository;
	private final GenericSyncService genericSyncService;
	private final PasswordResetOtpService passwordResetOtpService;
	private final EmailNotificationService emailNotificationService;

	@Override
	public OwnerIdentityResponse getOwnerIdentity(Long tenantId) {
		User owner = getPrimaryUser(tenantId);
		AuthProvider provider = owner.getAuthProvider() == null ? AuthProvider.PHONE : owner.getAuthProvider();
		return new OwnerIdentityResponse(owner.getLoginId(), owner.getWhatsappNumber(), owner.getEmail(),
				provider.name(), provider != AuthProvider.GOOGLE);
	}

	@Override
	public PushSyncResponse pushData(Long tenantId, List<User> payload) {
		return genericSyncService.handlePushSync(tenantId, payload, repository);
	}

	@Override
	@Transactional(readOnly = true)
	public List<User> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
	}

	@Override
	@Transactional
	public void requestMobileNumberUpdateOtp(Long tenantId, String newMobileNumber) {
		newMobileNumber = PhoneNormalizer.normalize(newMobileNumber);
		User currentUser = getPrimaryUser(tenantId);
		if (newMobileNumber.equals(currentUser.getWhatsappNumber())) {
			return;
		}

		ensureMobileNumberAvailable(tenantId, currentUser, newMobileNumber);
		passwordResetOtpService.issueMobileUpdateOtp(tenantId, newMobileNumber);
	}

	@Override
	@Transactional
	public void confirmMobileNumberUpdate(Long tenantId, String newMobileNumber, String otp) {
		newMobileNumber = PhoneNormalizer.normalize(newMobileNumber);
		User currentUser = getPrimaryUser(tenantId);
		if (newMobileNumber.equals(currentUser.getWhatsappNumber())) {
			return;
		}

		passwordResetOtpService.validateMobileUpdateOtpOrThrow(tenantId, newMobileNumber, otp);
		ensureMobileNumberAvailable(tenantId, currentUser, newMobileNumber);
		String previousEmail = currentUser.getEmail();
		updatePrimaryMobileNumber(tenantId, currentUser, newMobileNumber);
		// The old address/identifier stays dead after this commit; tell whoever still
		// owns the previous mailbox so a takeover is visible to the legitimate owner.
		notifyIdentityChangedAfterCommit(previousEmail, currentUser.getLoginId());
	}

	@Override
	@Transactional
	public void requestEmailUpdateOtp(Long tenantId, String newEmail) {
		newEmail = normalizeEmail(newEmail);
		User currentUser = getPrimaryUser(tenantId);
		if (newEmail.equals(currentUser.getEmail())) {
			return;
		}

		ensureEmailAvailable(tenantId, currentUser, newEmail);
		passwordResetOtpService.issueEmailUpdateOtp(tenantId, newEmail);
	}

	@Override
	@Transactional
	public void confirmEmailUpdate(Long tenantId, String newEmail, String otp) {
		newEmail = normalizeEmail(newEmail);
		User currentUser = getPrimaryUser(tenantId);
		if (newEmail.equals(currentUser.getEmail())) {
			return;
		}

		passwordResetOtpService.validateEmailUpdateOtpOrThrow(tenantId, newEmail, otp);
		ensureEmailAvailable(tenantId, currentUser, newEmail);
		String previousEmail = currentUser.getEmail();
		updatePrimaryEmail(tenantId, currentUser, newEmail);
		// The old address/identifier stays dead after this commit; tell whoever still
		// owns the previous mailbox so a takeover is visible to the legitimate owner.
		notifyIdentityChangedAfterCommit(previousEmail, currentUser.getLoginId());
	}

	/**
	 * Emails are matched case-insensitively and without surrounding whitespace, so the
	 * stored value and every comparison use this same form.
	 */
	private String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	private void ensureMobileNumberAvailable(Long tenantId, User currentUser, String newMobileNumber) {
		repository.findByLoginId(newMobileNumber)
				.filter(existing -> conflictsWithAnotherShop(existing, currentUser, tenantId))
				.ifPresent(existing -> {
					throw new IllegalArgumentException("This number is already related to another shop.");
				});

		repository.findByEmail(newMobileNumber)
				.filter(existing -> conflictsWithAnotherShop(existing, currentUser, tenantId))
				.ifPresent(existing -> {
					throw new IllegalArgumentException("This number is already related to another shop.");
				});

		repository.findByWhatsappNumber(newMobileNumber)
				.filter(existing -> conflictsWithAnotherShop(existing, currentUser, tenantId))
				.ifPresent(existing -> {
					throw new IllegalArgumentException("This number is already related to another shop.");
				});

		// phone_number is a login identifier too (AuthServiceImpl resolves sign-in through
		// it), so a number still held there by another account of this shop must block the
		// change even when login_id, email and whatsapp have already moved.
		repository.findByPhoneNumber(newMobileNumber)
				.filter(existing -> !isSameUser(existing, currentUser))
				.ifPresent(existing -> {
					if (!tenantId.equals(existing.getRestaurantId())) {
						throw new IllegalArgumentException("This number is already related to another shop.");
					}
					throw new IllegalArgumentException("This number is already related to another account.");
				});
	}

	private void ensureEmailAvailable(Long tenantId, User currentUser, String newEmail) {
		repository.findByEmail(newEmail)
				.filter(existing -> conflictsWithAnotherShop(existing, currentUser, tenantId))
				.ifPresent(existing -> {
					throw new IllegalArgumentException("This email is already related to another shop.");
				});

		repository.findByLoginId(newEmail)
				.filter(existing -> conflictsWithAnotherShop(existing, currentUser, tenantId))
				.ifPresent(existing -> {
					throw new IllegalArgumentException("This email is already related to another shop.");
				});
	}

	/**
	 * A row that belongs to the tenant being edited is never a conflict — otherwise the
	 * owner's own account would block its own update.
	 */
	private boolean conflictsWithAnotherShop(User existing, User currentUser, Long tenantId) {
		if (existing == null) {
			return false;
		}
		if (isSameUser(existing, currentUser)) {
			return false;
		}
		return !tenantId.equals(existing.getRestaurantId());
	}

	private boolean isSameUser(User existing, User currentUser) {
		return existing != null
				&& currentUser.getId() != null
				&& currentUser.getId().equals(existing.getId());
	}

	private User getPrimaryUser(Long tenantId) {
		List<User> users = repository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(1L));
		if (!users.isEmpty()) {
			return users.get(0);
		}
		List<User> ownerUsers = repository.findByRestaurantIdAndRoleAndIsDeletedFalse(tenantId, UserRole.OWNER);
		if (ownerUsers.isEmpty()) {
			throw new IllegalStateException("Primary user for restaurant not found.");
		}
		return ownerUsers.get(0);
	}

	private void updatePrimaryMobileNumber(Long tenantId, User currentUser, String newMobileNumber) {
		long now = System.currentTimeMillis();

		if (currentUser.getAuthProvider() == null || currentUser.getAuthProvider() == AuthProvider.PHONE) {
			currentUser.setLoginId(newMobileNumber);
			currentUser.setEmail(newMobileNumber);
			currentUser.setAuthProvider(AuthProvider.PHONE);
		}
		currentUser.setWhatsappNumber(newMobileNumber);
		currentUser.setPhoneNumber(newMobileNumber);
		currentUser.setUpdatedAt(now);
		currentUser.setServerUpdatedAt(now);
		currentUser.setDeviceId("server");
		repository.save(currentUser);

		restaurantProfileRepository.findByRestaurantId(tenantId).ifPresent(profile -> {
			profile.setWhatsappNumber(newMobileNumber);
			profile.setUpdatedAt(now);
			profile.setServerUpdatedAt(now);
			profile.markFieldsChanged(now, "whatsappnumber");
			profile.setDeviceId("server");
			restaurantProfileRepository.save(profile);
		});
	}

	private void updatePrimaryEmail(Long tenantId, User currentUser, String newEmail) {
		long now = System.currentTimeMillis();
		if (currentUser.getLoginId() != null && currentUser.getLoginId().contains("@")) {
			currentUser.setLoginId(newEmail);
		}
		currentUser.setEmail(newEmail);
		currentUser.setUpdatedAt(now);
		currentUser.setServerUpdatedAt(now);
		currentUser.setDeviceId("server");
		repository.save(currentUser);

		restaurantProfileRepository.findByRestaurantId(tenantId).ifPresent(profile -> {
			profile.setEmail(newEmail);
			profile.setUpdatedAt(now);
			profile.setServerUpdatedAt(now);
			profile.markFieldsChanged(now, "email");
			profile.setDeviceId("server");
			restaurantProfileRepository.save(profile);
		});
	}

	/**
	 * The identity change has already been committed, so the notice is sent only after
	 * the commit succeeds. Sending it earlier would tell the previous owner about a
	 * change that might still roll back.
	 */
	private void notifyIdentityChangedAfterCommit(String previousEmail, String newLoginId) {
		if (previousEmail == null || previousEmail.isBlank()) {
			return;
		}
		if (previousEmail.equalsIgnoreCase(newLoginId)) {
			return;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					sendIdentityChangedNotice(previousEmail, newLoginId);
				}
			});
			return;
		}
		sendIdentityChangedNotice(previousEmail, newLoginId);
	}

	private void sendIdentityChangedNotice(String previousEmail, String newLoginId) {
		try {
			emailNotificationService.sendLoginIdChangedNotice(previousEmail, newLoginId);
		} catch (RuntimeException e) {
			// Best effort by contract: the change is already saved, so a mail failure must
			// not surface to the owner as a failed save.
			log.warn("Could not send login-id-changed notice to {}: {}", previousEmail, e.getMessage());
		}
	}
}
