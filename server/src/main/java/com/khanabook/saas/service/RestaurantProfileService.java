package com.khanabook.saas.service;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import lombok.Data;
import java.util.List;

public interface RestaurantProfileService {

	PushSyncResponse pushData(Long tenantId, List<RestaurantProfile> payload);

	List<RestaurantProfile> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	CounterResponse incrementAndGetCounters(Long tenantId);

	@Data
	class CounterResponse {
		private Long dailyCounter;
		private Long lifetimeCounter;
	}
}
