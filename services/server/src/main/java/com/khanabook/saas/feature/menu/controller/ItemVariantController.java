package com.khanabook.saas.feature.menu.controller;
import com.khanabook.saas.feature.sync.validation.SyncPushGuard;
import com.khanabook.saas.feature.sync.data.SyncMapper;

import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.service.ItemVariantService;
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
import com.khanabook.saas.feature.sync.validation.SyncPushGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.core.security.TenantContext;

@RestController
@RequestMapping("/sync/itemvariant")
@RequiredArgsConstructor
public class ItemVariantController {
	private final ItemVariantService service;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<ItemVariantDTO> payload) {
		SyncPushGuard.requireMasterDataWriter();
		return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, ItemVariant.class)));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<ItemVariantDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), ItemVariantDTO.class));
	}
}
