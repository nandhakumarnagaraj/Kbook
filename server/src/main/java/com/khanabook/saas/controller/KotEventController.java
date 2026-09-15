package com.khanabook.saas.controller;

import com.khanabook.saas.entity.PermissionKey;
import com.khanabook.saas.service.PermissionService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.KotEventDTO;
import com.khanabook.saas.sync.service.KotEventSyncService;
import com.khanabook.saas.sync.validation.SyncPushGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.khanabook.saas.security.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/sync/kot-events")
@RequiredArgsConstructor
public class KotEventController {

	private final KotEventSyncService service;
	private final PermissionService permissionService;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<KotEventDTO> payload) {
		SyncPushGuard.validateBatchSize(payload);
		SyncPushGuard.requirePermission(PermissionKey.BILLING_CREATE.getKey(), permissionService);
		return ResponseEntity.ok(service.push(TenantContext.getCurrentTenant(), payload));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<KotEventDTO>> pull(@RequestParam Long lastSyncTimestamp,
			@RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(service.pull(TenantContext.getCurrentTenant(), lastSyncTimestamp,
				deviceId, ignoreDeviceId, Pageable.unpaged()));
	}
}