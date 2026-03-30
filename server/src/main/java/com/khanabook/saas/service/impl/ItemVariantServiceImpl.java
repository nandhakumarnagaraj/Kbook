package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.service.ItemVariantService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class ItemVariantServiceImpl implements ItemVariantService {
	private final ItemVariantRepository repository;
	private final MenuItemRepository menuItemRepository;
	private final GenericSyncService genericSyncService;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<ItemVariant> payload) {
		List<ItemVariant> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();

		for (ItemVariant variant : payload) {
			if (variant.getServerMenuItemId() == null && variant.getMenuItemId() != null) {
				Optional<MenuItem> item = menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId,
						variant.getDeviceId(), variant.getMenuItemId());

				if (item.isPresent()) {
					variant.setServerMenuItemId(item.get().getId());
				} else {
					Optional<MenuItem> serverItem = menuItemRepository.findById(variant.getMenuItemId());
					if (serverItem.isPresent() && serverItem.get().getRestaurantId().equals(tenantId)) {
						variant.setServerMenuItemId(serverItem.get().getId());
					} else {
						failedLocalIds.add(variant.getLocalId());
						continue;
					}
				}
			}
			toSync.add(variant);
		}

		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
		return response;
	}

	@Override
	public List<ItemVariant> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
	}
}
