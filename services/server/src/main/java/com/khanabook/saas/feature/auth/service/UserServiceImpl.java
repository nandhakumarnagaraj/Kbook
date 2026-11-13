package com.khanabook.saas.feature.auth.service;

import com.khanabook.saas.feature.auth.data.OwnerIdentityResponse;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.sync.service.GenericSyncService;
import com.khanabook.saas.core.utility.PhoneNormalizer;
import com.khanabook.saas.core.security.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserRepository repository;
	private final RestaurantProfileRepository restaurantProfileRepository;
	private final GenericSyncService genericSyncService;
	private final PhoneNormalizer phoneNormalizer;
	private final PasswordEncoder passwordEncoder;

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
		newMobileNumber = phoneNormalizer.normalize(newMobileNumber);
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
		newMobileNumber = phoneNormalizer.normalize(newMobileNumber);
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
		newEmail = com.khanabook.saas.core.utility.EmailValidator.normalize(newEmail);
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
		newEmail = com.khanabook.saas.core.utility.EmailValidator.normalize(newEmail);
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

	@Override
	@Transactional
	public void completePasswordReset(String loginId, String otp, String newPassword) {
		String encodedPassword = passwordEncoder.encode(newPassword);
		User user = repository.findByLoginIdOrEmail(loginId)
				.orElseThrow(() -> new IllegalArgumentException("User not found with loginId: " + loginId));
		user.setPasswordHash(encodedPassword);
		user.setUpdatedAt(System.currentTimeMillis());
		repository.save(user);
	}
}
