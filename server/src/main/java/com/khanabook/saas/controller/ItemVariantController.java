package com.khanabook.saas.controller;

import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.service.ItemVariantService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.validation.SyncPushGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.security.TenantContext;

@RestController
@RequestMapping("/sync/itemvariant")
@RequiredArgsConstructor
public class ItemVariantController {
	private final ItemVariantService service;
	private final com.khanabook.saas.service.PermissionService permissionService;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<ItemVariantDTO> payload) {
		SyncPushGuard.requirePermission(
				com.khanabook.saas.entity.PermissionKey.MENU_EDIT_PRICE.getKey(), permissionService);
		return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, ItemVariant.class)));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<ItemVariantDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), ItemVariantDTO.class));
	}
}
