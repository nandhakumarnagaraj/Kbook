package com.khanabook.saas.feature.auth.service;

import com.khanabook.saas.feature.auth.data.OwnerIdentityResponse;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import java.util.List;

public interface UserService {

	/** Login identity of the restaurant's owner account, for read-only display in settings. */
	OwnerIdentityResponse getOwnerIdentity(Long tenantId);

	PushSyncResponse pushData(Long tenantId, List<User> payload);

	List<User> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	void requestMobileNumberUpdateOtp(Long tenantId, String newMobileNumber);

	void confirmMobileNumberUpdate(Long tenantId, String newMobileNumber, String otp);

	void requestEmailUpdateOtp(Long tenantId, String newEmail);

	void confirmEmailUpdate(Long tenantId, String newEmail, String otp);


}
