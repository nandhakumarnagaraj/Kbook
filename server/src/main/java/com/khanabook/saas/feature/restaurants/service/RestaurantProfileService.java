package com.khanabook.saas.feature.restaurants.service;

import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
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
