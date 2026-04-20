package com.khanabook.saas.service.impl;

import com.khanabook.saas.exception.DuplicateMenuItemException;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.service.MenuItemService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {
	private final MenuItemRepository repository;
	private final CategoryRepository categoryRepository;
	private final GenericSyncService genericSyncService;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<MenuItem> payload) {
		List<MenuItem> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();

		for (MenuItem item : payload) {
			validateMenuItem(item);
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

						failedLocalIds.add(item.getLocalId());
						continue;
					}
				}
			}
			resolveDuplicateMenuItem(tenantId, item);
			toSync.add(item);
		}

		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
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
	}

	private void resolveDuplicateMenuItem(Long tenantId, MenuItem item) {
		if (item.getCategoryId() == null) {
			return;
		}

		Optional<MenuItem> duplicate = repository.findActiveDuplicateByNormalizedName(
				tenantId,
				item.getCategoryId(),
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
}
