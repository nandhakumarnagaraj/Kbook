package com.khanabook.saas.feature.billing.controller;
import com.khanabook.saas.feature.sync.validation.SyncPushGuard;
import com.khanabook.saas.feature.sync.data.SyncMapper;

import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.billing.service.BillItemService;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.auth.data.UserDTO;
import com.khanabook.saas.feature.billing.data.BillDTO;
import com.khanabook.saas.feature.billing.data.BillItemDTO;
import com.khanabook.saas.feature.billing.data.BillPaymentDTO;
import com.khanabook.saas.feature.menu.data.MenuItemDTO;
import com.khanabook.saas.feature.menu.data.CategoryDTO;
import com.khanabook.saas.feature.menu.data.ItemVariantDTO;
import com.khanabook.saas.feature.inventory.data.StockLogDTO;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileDTO;
import com.khanabook.saas.feature.billing.data.BillDTO;
import com.khanabook.saas.feature.billing.data.BillItemDTO;
import com.khanabook.saas.feature.billing.data.BillPaymentDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.core.security.TenantContext;

import com.khanabook.saas.feature.sync.validation.SyncPushGuard;

@RestController
@RequestMapping("/sync/bills/items")
@RequiredArgsConstructor
public class BillItemController {
	private static final Logger log = LoggerFactory.getLogger(BillItemController.class);
	private final BillItemService service;
	private final com.khanabook.saas.feature.staff.service.PermissionService permissionService;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<BillItemDTO> payload) {
		SyncPushGuard.validateBatchSize(payload);
		SyncPushGuard.requirePermission(
				com.khanabook.saas.feature.staff.data.PermissionKey.BILLING_CREATE.getKey(), permissionService);
		log.info("Received bill items push for {} items for Tenant: {}", payload.size(),
				TenantContext.getCurrentTenant());
		return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, BillItem.class)));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<BillItemDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), BillItemDTO.class));
	}
}
