package com.khanabook.saas.feature.inventory.controller;
import com.khanabook.saas.feature.sync.data.SyncMapper;

import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.inventory.service.StockLogService;
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

@RestController
@RequestMapping("/sync/stocklog")
@RequiredArgsConstructor
public class StockLogController {
	private final StockLogService service;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<StockLogDTO> payload) {
		return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, StockLog.class)));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<StockLogDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), StockLogDTO.class));
	}
}
