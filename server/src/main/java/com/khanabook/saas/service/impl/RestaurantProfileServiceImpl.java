package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.service.RestaurantProfileService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class RestaurantProfileServiceImpl implements RestaurantProfileService {
	private final RestaurantProfileRepository repository;
	private final GenericSyncService genericSyncService;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<RestaurantProfile> payload) {
		return genericSyncService.handlePushSync(tenantId, payload, repository);
	}

	@Override
	public List<RestaurantProfile> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
	}

	@Override
	@Transactional
	public RestaurantProfileService.CounterResponse incrementAndGetCounters(Long tenantId) {
		Long now = System.currentTimeMillis();
		String timezone = repository.findByRestaurantId(tenantId).map(RestaurantProfile::getTimezone)
				.orElse("Asia/Kolkata");
		ZoneId zoneId;
		try {
			zoneId = ZoneId.of(timezone);
		} catch (Exception e) {
			zoneId = ZoneId.of("Asia/Kolkata");
		}
		String today = LocalDate.now(zoneId).toString();

		int updated = repository.incrementCountersAtomic(tenantId, today, now);

		if (updated == 0) {
			throw new RuntimeException("Restaurant profile not found for ID: " + tenantId);
		}

		java.util.List<Object[]> result = repository.getCounters(tenantId);
		if (result == null || result.isEmpty()) {
			throw new RuntimeException("Failed to retrieve updated counters");
		}

		RestaurantProfileService.CounterResponse response = new RestaurantProfileService.CounterResponse();
		Object[] row = result.get(0);
		response.setDailyCounter(((Number) row[0]).longValue());
		response.setLifetimeCounter(((Number) row[1]).longValue());
		return response;
	}
}
