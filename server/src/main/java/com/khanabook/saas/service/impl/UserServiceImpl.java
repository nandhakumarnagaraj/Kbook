package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.service.PasswordResetOtpService;
import com.khanabook.saas.service.UserService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
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
	private final PasswordResetOtpService passwordResetOtpService;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<User> payload) {
		return genericSyncService.handlePushSync(tenantId, payload, repository);
	}

	@Override
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
		User currentUser = getPrimaryUser(tenantId);
		if (newMobileNumber.equals(currentUser.getWhatsappNumber())) {
			return;
		}

		ensureMobileNumberAvailable(tenantId, newMobileNumber);
		passwordResetOtpService.issueMobileUpdateOtp(tenantId, newMobileNumber);
	}

	@Override
	@Transactional
	public void confirmMobileNumberUpdate(Long tenantId, String newMobileNumber, String otp) {
		User currentUser = getPrimaryUser(tenantId);
		if (newMobileNumber.equals(currentUser.getWhatsappNumber())) {
			return;
		}

		passwordResetOtpService.validateMobileUpdateOtpOrThrow(tenantId, newMobileNumber, otp);
		ensureMobileNumberAvailable(tenantId, newMobileNumber);
		updatePrimaryMobileNumber(tenantId, currentUser, newMobileNumber);
	}

	private void ensureMobileNumberAvailable(Long tenantId, String newMobileNumber) {
		Optional<User> existingUserByLoginId = repository.findByLoginId(newMobileNumber);
		if (existingUserByLoginId.isPresent()) {
			User existingUser = existingUserByLoginId.get();
			if (!existingUser.getRestaurantId().equals(tenantId)) {
				throw new IllegalArgumentException("This number is already related to another shop.");
			}
		}

		Optional<User> existingUserByEmail = repository.findByEmail(newMobileNumber);
		if (existingUserByEmail.isPresent()) {
			User existingUser = existingUserByEmail.get();
			if (!existingUser.getRestaurantId().equals(tenantId)) {
				throw new IllegalArgumentException("This number is already related to another shop.");
			}
		}

		Optional<User> existingUserByWhatsapp = repository.findByWhatsappNumber(newMobileNumber);
		if (existingUserByWhatsapp.isPresent()) {
			User existingUser = existingUserByWhatsapp.get();
			if (!existingUser.getRestaurantId().equals(tenantId)) {
				throw new IllegalArgumentException("This number is already related to another shop.");
			}
		}
	}

	private User getPrimaryUser(Long tenantId) {
		List<User> users = repository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(1L));
		if (users.isEmpty()) {
			throw new IllegalStateException("Primary user for restaurant not found.");
		}

		return users.get(0);
	}

	private void updatePrimaryMobileNumber(Long tenantId, User currentUser, String newMobileNumber) {
		long now = System.currentTimeMillis();

		if (currentUser.getAuthProvider() == null || currentUser.getAuthProvider() == AuthProvider.PHONE) {
			currentUser.setLoginId(newMobileNumber);
			currentUser.setEmail(newMobileNumber);
			currentUser.setAuthProvider(AuthProvider.PHONE);
		}
		currentUser.setWhatsappNumber(newMobileNumber);
		currentUser.setUpdatedAt(now);
		currentUser.setServerUpdatedAt(now);
		repository.save(currentUser);

		restaurantProfileRepository.findByRestaurantId(tenantId).ifPresent(profile -> {
			profile.setWhatsappNumber(newMobileNumber);
			profile.setUpdatedAt(now);
			profile.setServerUpdatedAt(now);
			restaurantProfileRepository.save(profile);
		});
	}
}
