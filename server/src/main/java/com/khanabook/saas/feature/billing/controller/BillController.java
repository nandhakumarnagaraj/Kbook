package com.khanabook.saas.feature.billing.controller;
import com.khanabook.saas.feature.sync.validation.SyncPushGuard;
import com.khanabook.saas.feature.sync.data.SyncMapper;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.service.BillService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.core.security.TenantContext;

import com.khanabook.saas.feature.sync.validation.SyncPushGuard;

@RestController
@RequestMapping("/sync/bills")
@RequiredArgsConstructor
public class BillController {
	private final BillService service;
	private final com.khanabook.saas.feature.staff.service.PermissionService permissionService;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<BillDTO> payload) {
		SyncPushGuard.validateBatchSize(payload);
		SyncPushGuard.requirePermission(
				com.khanabook.saas.feature.staff.data.PermissionKey.BILLING_CREATE.getKey(), permissionService);
		return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, Bill.class)));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<BillDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp,
				deviceId, TenantContext.getCurrentTerminalId(), ignoreDeviceId,
				org.springframework.data.domain.Pageable.unpaged()).getContent(), BillDTO.class));
	}
}
