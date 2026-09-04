package com.khanabook.saas.controller;

import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.service.MenuItemService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.validation.SyncPushGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.security.TenantContext;

@RestController
@RequestMapping("/sync/menuitem")
@RequiredArgsConstructor
public class MenuItemController {
	private final MenuItemService service;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<MenuItemDTO> payload) {
		return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, MenuItem.class)));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<MenuItemDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), MenuItemDTO.class));
	}

	@PutMapping("/{menuItemId}/unavailable")
	public ResponseEntity<Void> markAsUnavailable(@PathVariable Long menuItemId) {
		SyncPushGuard.requireMasterDataWriter();
		service.markItemAsUnavailable(TenantContext.getCurrentTenant(), menuItemId);
		return ResponseEntity.ok().build();
	}

	@PutMapping("/unavailable/all")
	public ResponseEntity<Void> markAllAsUnavailable() {
		SyncPushGuard.requireMasterDataWriter();
		service.markAllItemsAsUnavailable(TenantContext.getCurrentTenant());
		return ResponseEntity.ok().build();
	}

	@PutMapping("/update-existing")
	public ResponseEntity<Void> updateExisting(@RequestBody List<MenuItemDTO> itemsToUpdate) {
		SyncPushGuard.requireMasterDataWriter();
		service.updateExistingMenuItems(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(itemsToUpdate, MenuItem.class));
		return ResponseEntity.ok().build();
	}
}
