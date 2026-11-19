package com.khanabook.saas.feature.restaurants.controller;
import com.khanabook.saas.feature.sync.data.SyncMapper;

import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.service.RestaurantProfileService;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.core.security.TenantContext;

@RestController
@RequestMapping("/sync/restaurantprofile")
@RequiredArgsConstructor
public class RestaurantProfileController {
	private final RestaurantProfileService service;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<RestaurantProfileDTO> payload) {
		return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, RestaurantProfile.class)));
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
