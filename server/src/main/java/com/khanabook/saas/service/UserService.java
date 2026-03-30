package com.khanabook.saas.service;

import com.khanabook.saas.entity.User;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import java.util.List;

public interface UserService {

	PushSyncResponse pushData(Long tenantId, List<User> payload);

	List<User> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	void requestMobileNumberUpdateOtp(Long tenantId, String newMobileNumber);

	void confirmMobileNumberUpdate(Long tenantId, String newMobileNumber, String otp);
}
