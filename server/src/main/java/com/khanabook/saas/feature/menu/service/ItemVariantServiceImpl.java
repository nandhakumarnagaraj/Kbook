package com.khanabook.saas.feature.menu.service;

import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.menu.service.ItemVariantService;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.sync.service.GenericSyncService;
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

@Service
@RequiredArgsConstructor
public class ItemVariantServiceImpl implements ItemVariantService {
	private final ItemVariantRepository repository;
	private final MenuItemRepository menuItemRepository;
	private final GenericSyncService genericSyncService;

	@Override
	@Transactional
	public PushSyncResponse pushData(Long tenantId, List<ItemVariant> payload) {
		List<ItemVariant> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();
		Map<Long, String> failedReasons = new HashMap<>();

		for (ItemVariant variant : payload) {
			validateVariant(variant);
			resolveServerMenuItemId(tenantId, variant);
			if (variant.getServerMenuItemId() == null) {
				addFailure(failedLocalIds, failedReasons, variant.getLocalId(),
						"Item variant menu item could not be resolved");
				continue;
			}
			toSync.add(variant);
		}

		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		recomputeParentVariantFlags(tenantId, toSync);
		response.getFailedLocalIds().addAll(failedLocalIds);

		response.getFailedReasons().putAll(failedReasons);
		return response;
	}

	/**
	 * Recomputes each affected parent's {@code has_variants} from the variant rows that
	 * just landed, rather than believing the flag the device declared.
	 *
	 * <p>Runs after the batch so the rows it reads are the ones this push wrote, and it
	 * runs for deletions too - that is the only way a parent can be demoted back to a
	 * simple item. Every terminal that touches a variant goes through this endpoint,
	 * including builds too old to send the field at all, so the flag can no longer be
	 * stranded in a stale state that hides variants from other terminals.
	 *
	 * <p>No @Transactional here on purpose: this is a self-invocation, so Spring's proxy
	 * would bypass it and the flush would fail with no active transaction. pushData
	 * carries the annotation instead, which also makes the variant rows and the flag
	 * land in one commit - no other terminal can pull a half-applied variant change.
	 */
	void recomputeParentVariantFlags(Long tenantId, List<ItemVariant> pushed) {
		java.util.Set<Long> parentIds = pushed.stream()
				.map(ItemVariant::getServerMenuItemId)
				.filter(java.util.Objects::nonNull)
				.collect(java.util.stream.Collectors.toSet());
		if (parentIds.isEmpty()) {
			return;
		}
		menuItemRepository.recomputeHasVariantsFlag(parentIds, tenantId, System.currentTimeMillis());
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

	/**
	 * Same resolution problem, same fix as
	 * {@link MenuItemServiceImpl#resolveServerCategoryId}: the device names its parent item by
	 * a Room row id that only maps to a server id when this device owns a {@code menuitems}
	 * row with that local id, which it often does not. The extra {@code serverMenuItemId}
	 * fallback reuses the association the server's own copy of the variant already holds, so a
	 * variant price edit lands instead of the whole record being rejected over a foreign key
	 * the edit never touched.
	 */
	private void resolveServerMenuItemId(Long tenantId, ItemVariant variant) {
		Long localMenuItemId = variant.getMenuItemId();

		if (variant.getServerMenuItemId() != null) {
			boolean ours = menuItemRepository.findById(variant.getServerMenuItemId())
					.filter(item -> tenantId.equals(item.getRestaurantId()))
					.isPresent();
			if (ours) {
				variant.setMenuItemId(variant.getServerMenuItemId());
				return;
			}
			variant.setServerMenuItemId(null);
		}

		if (localMenuItemId == null) {
			reuseExistingVariantParent(tenantId, variant);
			return;
		}

		Optional<MenuItem> resolved = menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId,
				variant.getDeviceId(), localMenuItemId);

		if (resolved.isEmpty()) {
			resolved = menuItemRepository.findById(localMenuItemId)
					.filter(item -> tenantId.equals(item.getRestaurantId()));
		}

		if (resolved.isEmpty()) {
			resolved = menuItemRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(localMenuItemId))
					.stream().findFirst();
		}

		if (resolved.isEmpty()) {
			resolved = existingVariantParent(tenantId, variant);
		}

		resolved.ifPresent(item -> {
			variant.setServerMenuItemId(item.getId());
			variant.setMenuItemId(item.getId());
		});
	}

	private void reuseExistingVariantParent(Long tenantId, ItemVariant variant) {
		existingVariantParent(tenantId, variant).ifPresent(item -> {
			variant.setServerMenuItemId(item.getId());
			variant.setMenuItemId(item.getId());
		});
	}

	private Optional<MenuItem> existingVariantParent(Long tenantId, ItemVariant variant) {
		if (variant.getId() == null) {
			return Optional.empty();
		}
		return repository.findById(variant.getId())
				.filter(existing -> tenantId.equals(existing.getRestaurantId()))
				.map(ItemVariant::getServerMenuItemId)
				.filter(java.util.Objects::nonNull)
				.flatMap(menuItemRepository::findById)
				.filter(item -> tenantId.equals(item.getRestaurantId()));
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
