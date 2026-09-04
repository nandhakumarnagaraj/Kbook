package com.khanabook.saas.controller;

import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.service.MenuItemService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
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
	private final com.khanabook.saas.service.PermissionService permissionService;

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
		com.khanabook.saas.sync.validation.SyncPushGuard.requirePermission(
				com.khanabook.saas.entity.PermissionKey.MENU_TOGGLE_AVAILABILITY.getKey(), permissionService);
		service.markItemAsUnavailable(TenantContext.getCurrentTenant(), menuItemId);
		return ResponseEntity.ok().build();
	}

	@PutMapping("/unavailable/all")
	public ResponseEntity<Void> markAllAsUnavailable() {
		com.khanabook.saas.sync.validation.SyncPushGuard.requirePermission(
				com.khanabook.saas.entity.PermissionKey.MENU_TOGGLE_AVAILABILITY.getKey(), permissionService);
		service.markAllItemsAsUnavailable(TenantContext.getCurrentTenant());
		return ResponseEntity.ok().build();
	}

	@PutMapping("/update-existing")
	public ResponseEntity<Void> updateExisting(@RequestBody List<MenuItemDTO> itemsToUpdate) {
		com.khanabook.saas.sync.validation.SyncPushGuard.requirePermission(
				com.khanabook.saas.entity.PermissionKey.MENU_EDIT_FULL.getKey(), permissionService);
		service.updateExistingMenuItems(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(itemsToUpdate, MenuItem.class));
		return ResponseEntity.ok().build();
	}
}
