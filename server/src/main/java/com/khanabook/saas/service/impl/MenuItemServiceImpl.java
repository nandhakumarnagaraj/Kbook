package com.khanabook.saas.service.impl;

import com.khanabook.saas.exception.DuplicateMenuItemException;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.StaffPermission;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.StaffPermissionRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.security.authz.MenuPushAuthorizer;
import com.khanabook.saas.security.authz.OfflineAuthDecider;
import com.khanabook.saas.service.MenuItemService;
import com.khanabook.saas.service.PermissionService;
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
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {
	private final MenuItemRepository repository;
	private final CategoryRepository categoryRepository;
	private final GenericSyncService genericSyncService;
	private final PermissionService permissionService;
	private final StaffPermissionRepository staffPermissionRepository;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<MenuItem> payload) {
		List<MenuItem> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();
		Map<Long, String> failedReasons = new HashMap<>();

		// Acting user + role for server-side authorization. KBOOK_ADMIN is exempt
		// (mirrors GenericSyncService). A non-admin actor with no resolvable userId
		// cannot be authorized — reject every row rather than silently accepting.
		Long actingUserId = TenantContext.getCurrentUserId();
		boolean isKbookAdmin = "KBOOK_ADMIN".equals(TenantContext.getCurrentRole());

		for (MenuItem item : payload) {
			validateMenuItem(item);

			// ── P0: server-side menu permission enforcement ──────────────────
			// Detect what this push actually changes vs the existing server row
			// and require the matching fine-grained permission. Runs BEFORE the
			// row reaches GenericSyncService's LWW upsert.
			if (!isKbookAdmin) {
				if (actingUserId == null) {
					addFailure(failedLocalIds, failedReasons, item.getLocalId(),
							"Cannot authorize menu change: unknown user");
					continue;
				}
				MenuItem existing = resolveExisting(tenantId, item);
				MenuPushAuthorizer.Result authz = MenuPushAuthorizer.authorize(
						item,
						existing,
						/* revisionAtCreation (P1) */ item.getPermissionRevisionAtCreation(),
						/* terminalValid: menu push has no terminal-token gate */ true,
						menuFactsResolver(tenantId, actingUserId));
				if (authz.decision() != OfflineAuthDecider.Decision.ACCEPT) {
					addFailure(failedLocalIds, failedReasons, item.getLocalId(),
							authFailureMessage(authz));
					continue;
				}
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

	// ── P0 authorization helpers ────────────────────────────────────────────

	/**
	 * Resolve the current server row for an incoming push so the authorizer can
	 * diff it. Prefers the server id, falls back to (deviceId, localId). Returns
	 * null when no server row exists yet (⇒ treated as a create).
	 */
	private MenuItem resolveExisting(Long tenantId, MenuItem item) {
		if (item.getId() != null) {
			return repository.findById(item.getId())
					.filter(m -> tenantId != null && tenantId.equals(m.getRestaurantId()))
					.orElse(null);
		}
		if (item.getDeviceId() != null && item.getLocalId() != null) {
			return repository.findByRestaurantIdAndDeviceIdAndLocalId(
					tenantId, item.getDeviceId(), item.getLocalId()).orElse(null);
		}
		return null;
	}

	/**
	 * Supplies live permission facts (granted-now + last-revoked-revision) per key
	 * from persistent staff-permission state. OWNER/KBOOK_ADMIN grants are handled
	 * by {@link PermissionService#hasPermission}; a per-key revocation marker only
	 * ever exists for real staff rows.
	 */
	private MenuPushAuthorizer.FactsResolver menuFactsResolver(Long tenantId, Long userId) {
		final String editFull = com.khanabook.saas.entity.PermissionKey.MENU_EDIT_FULL.getKey();
		return MenuPushAuthorizer.factsResolver(
				requiredKey -> {
					// Direct grant, or satisfied by menu.edit_full (single implication rule).
					if (permissionService.hasPermission(tenantId, userId, requiredKey)) return true;
					return com.khanabook.saas.security.authz.MenuChangeType.satisfies(requiredKey, editFull)
							&& permissionService.hasPermission(tenantId, userId, editFull);
				},
				requiredKey -> {
					// Use the revocation marker of whichever key actually authorizes the op:
					// the required key if held directly, otherwise menu.edit_full.
					boolean directlyHeld = permissionService.hasPermission(tenantId, userId, requiredKey);
					String authorizingKey = directlyHeld ? requiredKey
							: (com.khanabook.saas.security.authz.MenuChangeType.satisfies(requiredKey, editFull)
									? editFull : requiredKey);
					return staffPermissionRepository
							.findByRestaurantIdAndUserIdAndPermissionKey(tenantId, userId, authorizingKey)
							.map(StaffPermission::getLastRevokedRevision)
							.orElse(null);
				});
	}

	private String authFailureMessage(MenuPushAuthorizer.Result authz) {
		String action = switch (authz.changeType()) {
			case PRICE, METADATA_ONLY -> "change the price of this item";
			case AVAILABILITY -> "change item availability";
			case PRICE_AND_AVAILABILITY -> "change price and availability";
			case CREATE -> "add menu items";
			case DELETE -> "remove menu items";
			case NONE -> "modify this item";
		};
		return "Not permitted to " + action + " (" + authz.reason() + ")";
	}

	@Override
	@Transactional
	public void markItemAsUnavailable(Long tenantId, Long menuItemId) {
		long now = System.currentTimeMillis();
		int updated = repository.markAsUnavailable(menuItemId, tenantId, now);
		if (updated == 0) {
			throw new IllegalArgumentException("Menu item not found or already unavailable");
		}
	}

	@Override
	@Transactional
	public void markAllItemsAsUnavailable(Long tenantId) {
		long now = System.currentTimeMillis();
		repository.markAllAsUnavailable(tenantId, now);
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
	}
}
