package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.service.BillItemService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillItemServiceImpl implements BillItemService {
	private final BillItemRepository repository;
	private final BillRepository billRepository;
	private final MenuItemRepository menuItemRepository;
	private final ItemVariantRepository itemVariantRepository;
	private final GenericSyncService genericSyncService;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<BillItem> payload) {
		List<BillItem> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();
		Map<Long, String> failedReasons = new HashMap<>();

		for (BillItem item : payload) {
			boolean resolved = true;

			Long billLookupId = item.getBillId() != null ? item.getBillId() : item.getLocalId();
			if (item.getServerBillId() == null && billLookupId != null) {
				Optional<Bill> bill = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId,
						item.getDeviceId(), billLookupId);
				if (bill.isPresent()) {
					item.setServerBillId(bill.get().getId());
				} else {
					billRepository.findById(billLookupId)
							.filter(b -> b.getRestaurantId().equals(tenantId))
							.ifPresent(b -> item.setServerBillId(b.getId()));
				}
				if (item.getServerBillId() == null) {
					billRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(billLookupId))
							.stream().findFirst()
							.ifPresent(b -> item.setServerBillId(b.getId()));
				}
			}

			Long menuItemLookupId = item.getMenuItemId() != null ? item.getMenuItemId() : item.getLocalId();
			if (item.getServerMenuItemId() == null && menuItemLookupId != null) {
				Optional<MenuItem> mi = menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId,
						item.getDeviceId(), menuItemLookupId);
				if (mi.isPresent()) {
					item.setServerMenuItemId(mi.get().getId());
				} else {
					menuItemRepository.findById(menuItemLookupId)
							.filter(m -> m.getRestaurantId().equals(tenantId))
							.ifPresent(m -> item.setServerMenuItemId(m.getId()));
				}
				if (item.getServerMenuItemId() == null) {
					menuItemRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(menuItemLookupId))
							.stream().findFirst()
							.ifPresent(m -> item.setServerMenuItemId(m.getId()));
				}
			}

			Long variantLookupId = item.getVariantId() != null && item.getVariantId() > 0 ? item.getVariantId() : null;
			if (item.getServerVariantId() == null && variantLookupId != null) {
				Optional<ItemVariant> iv = itemVariantRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId,
						item.getDeviceId(), variantLookupId);
				if (iv.isPresent()) {
					item.setServerVariantId(iv.get().getId());
				} else {
					itemVariantRepository.findById(variantLookupId)
							.filter(v -> v.getRestaurantId().equals(tenantId))
							.ifPresent(v -> item.setServerVariantId(v.getId()));
				}
				if (item.getServerVariantId() == null) {
					itemVariantRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(variantLookupId))
							.stream().findFirst()
							.ifPresent(v -> item.setServerVariantId(v.getId()));
				}
			}

			if (item.getServerBillId() == null) {
				addFailure(failedLocalIds, failedReasons, item.getLocalId(),
						"Bill item parent bill could not be resolved");
				resolved = false;
			}
			if (item.getMenuItemId() == null && item.getServerMenuItemId() == null) {
				item.setMenuItemId(0L);
			}

			if (resolved) {
				toSync.add(item);
			}
		}
		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
		response.getFailedReasons().putAll(failedReasons);
		return response;
	}

	@Override
	@Transactional(readOnly = true)
	public List<BillItem> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
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
