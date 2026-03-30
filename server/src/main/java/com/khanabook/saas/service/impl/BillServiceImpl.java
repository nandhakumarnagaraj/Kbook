package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.service.BillService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {
	private final BillRepository repository;
	private final GenericSyncService genericSyncService;
	private final RestaurantProfileRepository profileRepository;
	private final java.util.Map<Long, String> timezoneCache = java.util.Collections.synchronizedMap(
			new java.util.LinkedHashMap<Long, String>(101, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(java.util.Map.Entry<Long, String> eldest) {
					return size() > 100;
				}
			}
	);

	@Override
	public PushSyncResponse pushData(Long tenantId, List<Bill> payload) {
		String timezone = timezoneCache.computeIfAbsent(tenantId, tid -> profileRepository.findByRestaurantId(tid)
				.map(RestaurantProfile::getTimezone).orElse("Asia/Kolkata"));
		ZoneId zoneId;
		try {
			zoneId = ZoneId.of(timezone);
		} catch (Exception e) {
			zoneId = ZoneId.of("Asia/Kolkata");
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zoneId);

		for (Bill bill : payload) {
			if (bill.getLastResetDate() == null) {

				Long created = bill.getCreatedAt() != null ? bill.getCreatedAt() : System.currentTimeMillis();
				bill.setLastResetDate(formatter.format(Instant.ofEpochMilli(created)));
			}
		}
		return genericSyncService.handlePushSync(tenantId, payload, repository);
	}

	@Override
	public List<Bill> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
	}
}
