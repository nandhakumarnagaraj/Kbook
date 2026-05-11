package com.khanabook.saas.billing.service.impl;

import com.khanabook.saas.billing.domain.Bill;
import com.khanabook.saas.inventory.domain.MenuItem;
import com.khanabook.saas.inventory.domain.ItemVariant;
import com.khanabook.saas.billing.domain.BillItem;
import com.khanabook.saas.billing.repository.BillRepository;
import com.khanabook.saas.inventory.repository.MenuItemRepository;
import com.khanabook.saas.inventory.repository.ItemVariantRepository;
import com.khanabook.saas.billing.repository.BillItemRepository;
import com.khanabook.saas.billing.service.BillItemService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import com.khanabook.saas.common.BatchQueryUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
		Set<Long> billLocalIds = new HashSet<>();
		Set<Long> menuItemLocalIds = new HashSet<>();
		Set<Long> variantLocalIds = new HashSet<>();
		Set<String> deviceIds = new HashSet<>();

		for (BillItem item : payload) {
			if (item.getServerBillId() == null && item.getBillId() != null) {
				billLocalIds.add(item.getBillId());
			}
			if (item.getServerMenuItemId() == null && item.getMenuItemId() != null) {
				menuItemLocalIds.add(item.getMenuItemId());
			}
			if (item.getServerVariantId() == null && item.getVariantId() != null && item.getVariantId() > 0) {
				variantLocalIds.add(item.getVariantId());
			}
			if (item.getDeviceId() != null) {
				deviceIds.add(item.getDeviceId());
			}
		}

		Map<Long, Long> billLocalToServerId = resolveBillIds(tenantId, billLocalIds, deviceIds);
		Map<Long, Long> menuItemLocalToServerId = resolveMenuItemIds(tenantId, menuItemLocalIds, deviceIds);
		Map<Long, Long> variantLocalToServerId = resolveVariantIds(tenantId, variantLocalIds, deviceIds);

		List<BillItem> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();

		for (BillItem item : payload) {
			if (item.getServerBillId() == null && item.getBillId() != null) {
				item.setServerBillId(billLocalToServerId.get(item.getBillId()));
			}
			if (item.getServerMenuItemId() == null && item.getMenuItemId() != null) {
				item.setServerMenuItemId(menuItemLocalToServerId.get(item.getMenuItemId()));
			}
			if (item.getServerVariantId() == null && item.getVariantId() != null && item.getVariantId() > 0) {
				item.setServerVariantId(variantLocalToServerId.get(item.getVariantId()));
			}

			if (item.getServerBillId() == null || item.getServerMenuItemId() == null) {
				failedLocalIds.add(item.getLocalId());
			} else {
				toSync.add(item);
			}
		}
		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
		return response;
	}

	private Map<Long, Long> resolveBillIds(Long tenantId, Set<Long> localIds, Set<String> deviceIds) {
		Map<Long, Long> result = new HashMap<>();
		if (localIds.isEmpty()) return result;
		for (String deviceId : deviceIds) {
			String dev = deviceId;
			List<Bill> bills = BatchQueryUtil.queryInBatches(localIds, batch ->
					billRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, dev, batch));
			for (Bill bill : bills) {
				result.put(bill.getLocalId(), bill.getId());
			}
		}
		List<Bill> fallbackBills = BatchQueryUtil.queryInBatches(localIds, batch ->
				billRepository.findByRestaurantIdAndLocalIdIn(tenantId, batch));
		for (Bill bill : fallbackBills) {
			result.putIfAbsent(bill.getLocalId(), bill.getId());
		}
		return result;
	}

	private Map<Long, Long> resolveMenuItemIds(Long tenantId, Set<Long> localIds, Set<String> deviceIds) {
		Map<Long, Long> result = new HashMap<>();
		if (localIds.isEmpty()) return result;
		for (String deviceId : deviceIds) {
			String dev = deviceId;
			List<MenuItem> items = BatchQueryUtil.queryInBatches(localIds, batch ->
					menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, dev, batch));
			for (MenuItem item : items) {
				result.put(item.getLocalId(), item.getId());
			}
		}
		List<MenuItem> fallbackItems = BatchQueryUtil.queryInBatches(localIds, batch ->
				menuItemRepository.findByRestaurantIdAndLocalIdIn(tenantId, batch));
		for (MenuItem item : fallbackItems) {
			result.putIfAbsent(item.getLocalId(), item.getId());
		}
		return result;
	}

	private Map<Long, Long> resolveVariantIds(Long tenantId, Set<Long> localIds, Set<String> deviceIds) {
		Map<Long, Long> result = new HashMap<>();
		if (localIds.isEmpty()) return result;
		for (String deviceId : deviceIds) {
			String dev = deviceId;
			List<ItemVariant> variants = BatchQueryUtil.queryInBatches(localIds, batch ->
					itemVariantRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, dev, batch));
			for (ItemVariant variant : variants) {
				result.put(variant.getLocalId(), variant.getId());
			}
		}
		List<ItemVariant> fallbackVariants = BatchQueryUtil.queryInBatches(localIds, batch ->
				itemVariantRepository.findByRestaurantIdAndLocalIdIn(tenantId, batch));
		for (ItemVariant variant : fallbackVariants) {
			result.putIfAbsent(variant.getLocalId(), variant.getId());
		}
		return result;
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

	@Override
	@Transactional(readOnly = true)
	public Page<BillItem> pullDataPaginated(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, Pageable pageable) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp, pageable);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(
				tenantId, lastSyncTimestamp, deviceId, pageable);
	}
}
