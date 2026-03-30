package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.Category;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.service.CategoryService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
	private final CategoryRepository repository;
	private final GenericSyncService genericSyncService;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<Category> payload) {
		return genericSyncService.handlePushSync(tenantId, payload, repository);
	}

	@Override
	public List<Category> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
	}
}
