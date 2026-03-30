package com.khanabook.saas.controller;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.service.RestaurantProfileService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.security.TenantContext;

@RestController
@RequestMapping("/sync/restaurantprofile")
@RequiredArgsConstructor
public class RestaurantProfileController {
	private final RestaurantProfileService service;
	private final GenericSyncService genericSyncService;
	private final RestaurantProfileRepository restaurantProfileRepository;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<RestaurantProfileDTO> payload) {
		return ResponseEntity.ok(genericSyncService.handlePushSync(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, RestaurantProfile.class), restaurantProfileRepository));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<RestaurantProfileDTO>> pull(@RequestParam Long lastSyncTimestamp,
			@RequestParam String deviceId, @RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), RestaurantProfileDTO.class));
	}

	@PostMapping("/counters/increment")
	public ResponseEntity<RestaurantProfileService.CounterResponse> incrementCounters() {
		return ResponseEntity.ok(service.incrementAndGetCounters(TenantContext.getCurrentTenant()));
	}
}
