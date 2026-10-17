package com.khanabook.saas.feature.menu.controller;
import com.khanabook.saas.feature.sync.validation.SyncPushGuard;
import com.khanabook.saas.feature.sync.data.SyncMapper;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.menu.service.CategoryService;
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

@RestController
@RequestMapping("/sync/menu/categories")
@RequiredArgsConstructor
public class CategoryController {
	private final CategoryService service;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<CategoryDTO> payload) {
		SyncPushGuard.requireMasterDataWriter();
		return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, Category.class)));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<CategoryDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), CategoryDTO.class));
	}
}
