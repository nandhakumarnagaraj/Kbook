package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.service.ItemVariantService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import com.khanabook.saas.utility.PricingConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
		Map<Long, String> failedReasons = new HashMap<>();

		for (ItemVariant variant : payload) {
			validateVariant(variant);
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
						menuItemRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(variant.getMenuItemId()))
								.stream().findFirst()
								.ifPresent(m -> variant.setServerMenuItemId(m.getId()));
					}
				}
			}
			if (variant.getServerMenuItemId() == null && variant.getMenuItemId() != null) {
				addFailure(failedLocalIds, failedReasons, variant.getLocalId(),
						"Item variant menu item could not be resolved");
				continue;
			}
			toSync.add(variant);
		}

		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
		response.getFailedReasons().putAll(failedReasons);
		return response;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ItemVariant> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
	}

	private void validateVariant(ItemVariant variant) {
		if (variant.getPrice() == null) {
			throw new IllegalArgumentException("Enter a valid item price");
		}
		if (variant.getPrice().compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Price cannot be negative");
		}
		if (variant.getPrice().compareTo(PricingConstants.MAX_ITEM_PRICE) > 0) {
			throw new IllegalArgumentException("Price must be between Rs. 0 and Rs. 1,00,000");
		}
	}

	private void addFailure(List<Long> failedLocalIds, Map<Long, String> failedReasons, Long localId, String reason) {
		if (localId == null) {
			return;
		}
		if (!failedLocalIds.contains(localId)) {
			failedLocalIds.add(localId);
		}
		failedReasons.put(localId, reason);
	}
}
