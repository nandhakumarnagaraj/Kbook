package com.khanabook.saas.service.impl;

import com.khanabook.saas.exception.DuplicateMenuItemException;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.MenuItemService;
import com.khanabook.saas.service.PushNotificationService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import com.khanabook.saas.sync.validation.SyncPushGuard;
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
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {
	private final MenuItemRepository repository;
	private final CategoryRepository categoryRepository;
	private final GenericSyncService genericSyncService;
	private final PushNotificationService pushNotificationService;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<MenuItem> payload) {
		List<MenuItem> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();
		Map<Long, String> failedReasons = new HashMap<>();

		// Master data is single-writer: only OWNER / SHOP_ADMIN / KBOOK_ADMIN may
		// write the menu. Staff terminals are bill-mints that read the cached menu;
		// a staff push is rejected per record (failedReasons in a 200 batch) so the
		// device sync loop keeps running instead of hard-failing on a 403.
		String role = TenantContext.getCurrentRole();
		boolean isMasterDataWriter = SyncPushGuard.isMasterDataWriter(role);
		Long actingUserId = TenantContext.getCurrentUserId();

		for (MenuItem item : payload) {
			validateMenuItem(item);

			if (!isMasterDataWriter) {
				addFailure(failedLocalIds, failedReasons, item.getLocalId(),
						"Only the restaurant owner or an admin may change menu items");
				continue;
			}
			if (actingUserId == null && !"KBOOK_ADMIN".equals(role)) {
				addFailure(failedLocalIds, failedReasons, item.getLocalId(),
						"Cannot authorize menu change: unknown user");
				continue;
			}

			if (item.getServerCategoryId() == null && item.getCategoryId() != null) {
				Optional<Category> category = categoryRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId,
						item.getDeviceId(), item.getCategoryId());

				if (category.isPresent()) {
					item.setServerCategoryId(category.get().getId());
				} else {

					Optional<Category> serverCategory = categoryRepository.findById(item.getCategoryId());
					if (serverCategory.isPresent() && serverCategory.get().getRestaurantId().equals(tenantId)) {
						item.setServerCategoryId(serverCategory.get().getId());
					} else {
						categoryRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(item.getCategoryId()))
								.stream().findFirst()
								.ifPresent(c -> item.setServerCategoryId(c.getId()));
					}
				}
			}
			if (item.getServerCategoryId() == null && item.getCategoryId() != null) {
				addFailure(failedLocalIds, failedReasons, item.getLocalId(),
						"Menu item category could not be resolved");
				continue;
			}
			resolveDuplicateMenuItem(tenantId, item);
			toSync.add(item);
		}

		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
		response.getFailedReasons().putAll(failedReasons);
		// Near-real-time propagation: tell every other device to sync now so a
		// price/name/stock edit lands within seconds, not the 2-minute window.
		if (!toSync.isEmpty()) {
			pushNotificationService.pushSyncNow(tenantId);
		}
		return response;
	}

	@Override
	@Transactional(readOnly = true)
	public List<MenuItem> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
	}

	private void validateMenuItem(MenuItem item) {
		String collapsedName = collapseWhitespace(item.getName());
		if (collapsedName.isBlank()) {
			throw new IllegalArgumentException("Item name is required");
		}
		item.setName(collapsedName);

		if (item.getBasePrice() == null) {
			throw new IllegalArgumentException("Enter a valid item price");
		}
		if (item.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Price cannot be negative");
		}
		if (item.getBasePrice().compareTo(PricingConstants.MAX_ITEM_PRICE) > 0) {
			throw new IllegalArgumentException("Price must be between Rs. 0 and Rs. 1,00,000");
		}
	}

	private void resolveDuplicateMenuItem(Long tenantId, MenuItem item) {
		Long categoryIdToUse = item.getServerCategoryId() != null ? item.getServerCategoryId() : item.getCategoryId();
		if (categoryIdToUse == null) {
			return;
		}

		Optional<MenuItem> duplicate = repository.findActiveDuplicateByNormalizedName(
				tenantId,
				categoryIdToUse,
				normalizeMenuItemName(item.getName())
		);

		if (duplicate.isEmpty()) {
			return;
		}

		MenuItem existing = duplicate.get();
		if (isSameMenuItemRecord(tenantId, item, existing)) {
			return;
		}

		if (Boolean.TRUE.equals(item.getOverwriteExisting())) {
			item.setId(existing.getId());
			return;
		}

		throw new DuplicateMenuItemException("Item already exists in this category");
	}

	private boolean isSameMenuItemRecord(Long tenantId, MenuItem incoming, MenuItem existing) {
		if (incoming.getId() != null && incoming.getId().equals(existing.getId())) {
			return true;
		}

		if (incoming.getDeviceId() != null && incoming.getLocalId() != null) {
			return repository.findByRestaurantIdAndDeviceIdAndLocalId(
					tenantId,
					incoming.getDeviceId(),
					incoming.getLocalId()
			).map(record -> record.getId() != null && record.getId().equals(existing.getId()))
					.orElse(false);
		}

		return false;
	}

	private String collapseWhitespace(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ");
	}

	private String normalizeMenuItemName(String value) {
		return collapseWhitespace(value).toLowerCase(Locale.ROOT);
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

	// ── helpers ────────────────────────────────────────────────────────────

	@Override
	@Transactional
	public void markItemAsUnavailable(Long tenantId, Long menuItemId) {
		long now = System.currentTimeMillis();
		int updated = repository.markAsUnavailable(menuItemId, tenantId, now);
		if (updated == 0) {
			throw new IllegalArgumentException("Menu item not found or already unavailable");
		}
		pushNotificationService.pushSyncNow(tenantId);
	}

	@Override
	@Transactional
	public void markAllItemsAsUnavailable(Long tenantId) {
		long now = System.currentTimeMillis();
		repository.markAllAsUnavailable(tenantId, now);
		pushNotificationService.pushSyncNow(tenantId);
	}

	@Override
	@Transactional
	public void updateExistingMenuItems(Long tenantId, List<MenuItem> itemsToUpdate) {
		long now = System.currentTimeMillis();
		for (MenuItem item : itemsToUpdate) {
			Optional<MenuItem> existing = repository.findById(item.getId());
			if (existing.isPresent() && existing.get().getRestaurantId().equals(tenantId)) {
				MenuItem toUpdate = existing.get();
				if (item.getName() != null) toUpdate.setName(item.getName());
				if (item.getBasePrice() != null) toUpdate.setBasePrice(item.getBasePrice());
				if (item.getDescription() != null) toUpdate.setDescription(item.getDescription());
				if (item.getFoodType() != null) toUpdate.setFoodType(item.getFoodType());
				if (item.getCategoryId() != null) toUpdate.setCategoryId(item.getCategoryId());
				if (item.getIsAvailable() != null) toUpdate.setIsAvailable(item.getIsAvailable());
				toUpdate.setUpdatedAt(now);
				toUpdate.setServerUpdatedAt(now);
				repository.save(toUpdate);
			}
		}
		pushNotificationService.pushSyncNow(tenantId);
	}
}
