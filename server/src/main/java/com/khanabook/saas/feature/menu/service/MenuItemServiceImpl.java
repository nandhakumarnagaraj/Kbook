package com.khanabook.saas.feature.menu.service;
import com.khanabook.saas.feature.sync.validation.SyncPushGuard;

import com.khanabook.saas.core.exception.DuplicateMenuItemException;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.menu.service.MenuItemService;
import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.sync.service.GenericSyncService;
import com.khanabook.saas.feature.sync.validation.SyncPushGuard;
import com.khanabook.saas.core.utility.PricingConstants;
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
import java.util.Objects;

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

		// Master data is single-writer: only OWNER / KBOOK_ADMIN may
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

			resolveServerCategoryId(tenantId, item);
			if (item.getServerCategoryId() == null) {
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

		// menuitems.has_variants is NOT NULL, but a client build that predates the field
		// omits it and SyncMapper copies the omission through as null, so an INSERT from
		// such a build failed the batch with a 409. Default a genuinely new row to false.
		// Updates are deliberately not defaulted here: an update carries a server id
		// (MenuItemDTO.id, serialized as "serverId"), and null there means
		// "unchanged/unknown", which GenericSyncService resolves from the stored row
		// instead. Defaulting those too would silently demote a variant container whose
		// owner just edited its price.
		if (item.getHasVariants() == null && item.getId() == null) {
			item.setHasVariants(false);
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

	/**
	 * Works out which server-side category a pushed menu item belongs to, leaving the answer
	 * in {@code serverCategoryId} (and normalising {@code categoryId} to the same server id,
	 * which is what the {@code menuitems.category_id} column actually stores).
	 *
	 * <p>The device identifies its category by the Room row id it happens to have, and that id
	 * is only meaningful to the server when the device also owns a {@code categories} row with
	 * the same local id. It routinely does not: device ids rotate on reinstall, a category may
	 * have been created by a different device under the same local id, or the category's own
	 * push may not have landed yet. The original lookup chain gave up after three id-only
	 * guesses and rejected the whole record, so a perfectly valid name/price edit was thrown
	 * away over a foreign key the edit never touched - and, because that rejection reads as
	 * "all records failed", it also drove the client into a full re-pull that reverted the
	 * very edit that was rejected.
	 *
	 * <p>Lookup order:
	 * <ol>
	 *   <li>the id the client already claims is a server category id, once verified as ours -
	 *       a client that has already been through a pull knows the answer;</li>
	 *   <li>same device + same local id (a category this device created);</li>
	 *   <li>the id is a server id (a row adopted from a pull keeps both equal);</li>
	 *   <li>same local id under any device of this restaurant (device ids rotate);</li>
	 *   <li>the category the server row already points at, when this push is an edit - the
	 *       stored association is known good, and reusing it lets the edit land instead of
	 *       failing the record on a stale client-side id.</li>
	 * </ol>
	 */
	private void resolveServerCategoryId(Long tenantId, MenuItem item) {
		Long localCategoryId = item.getCategoryId();

		// A server id the client already knows: trust it only after confirming it is ours,
		// otherwise a client could point a menu item at another restaurant's category.
		if (item.getServerCategoryId() != null) {
			boolean ours = categoryRepository.findById(item.getServerCategoryId())
					.filter(c -> tenantId.equals(c.getRestaurantId()))
					.isPresent();
			if (ours) {
				item.setCategoryId(item.getServerCategoryId());
				return;
			}
			item.setServerCategoryId(null);
		}

		if (localCategoryId == null) {
			reuseExistingItemCategory(tenantId, item);
			return;
		}

		Optional<Category> resolved = categoryRepository
				.findByRestaurantIdAndDeviceIdAndLocalId(tenantId, item.getDeviceId(), localCategoryId);

		if (resolved.isEmpty()) {
			resolved = categoryRepository.findById(localCategoryId)
					.filter(c -> tenantId.equals(c.getRestaurantId()));
		}

		if (resolved.isEmpty()) {
			resolved = categoryRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(localCategoryId))
					.stream().findFirst();
		}

		if (resolved.isEmpty()) {
			resolved = existingItemCategory(tenantId, item);
		}

		if (resolved.isPresent()) {
			item.setServerCategoryId(resolved.get().getId());
			item.setCategoryId(resolved.get().getId());
		}
	}

	private void reuseExistingItemCategory(Long tenantId, MenuItem item) {
		existingItemCategory(tenantId, item).ifPresent(category -> {
			item.setServerCategoryId(category.getId());
			item.setCategoryId(category.getId());
		});
	}

	/**
	 * The category the server's own copy of this item already uses. Only meaningful for an
	 * edit (the incoming record carries a server id); returns empty for a new item.
	 */
	private Optional<Category> existingItemCategory(Long tenantId, MenuItem item) {
		if (item.getId() == null) {
			return Optional.empty();
		}
		return repository.findById(item.getId())
				.filter(existing -> tenantId.equals(existing.getRestaurantId()))
				.map(MenuItem::getServerCategoryId)
				.filter(Objects::nonNull)
				.flatMap(categoryRepository::findById)
				.filter(category -> tenantId.equals(category.getRestaurantId()));
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
	public void markItemAsAvailable(Long tenantId, Long menuItemId) {
		long now = System.currentTimeMillis();
		int updated = repository.markAsAvailable(menuItemId, tenantId, now);
		if (updated == 0) {
			throw new IllegalArgumentException("Menu item not found or deleted");
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
				// The create path runs validateMenuItem, but this one did not, so the cap was
				// bypassable via PUT /sync/menuitem/update-existing.
				if (item.getBasePrice() != null) {
					if (item.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
						throw new IllegalArgumentException("Price cannot be negative");
					}
					if (item.getBasePrice().compareTo(PricingConstants.MAX_ITEM_PRICE) > 0) {
						throw new IllegalArgumentException("Price must be between Rs. 0 and Rs. 1,00,000");
					}
					toUpdate.setBasePrice(item.getBasePrice());
				}
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
