package com.khanabook.saas.controller;

import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.service.BillItemService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.security.TenantContext;

@RestController
@RequestMapping("/sync/bills/items")
@RequiredArgsConstructor
public class BillItemController {
	private static final Logger log = LoggerFactory.getLogger(BillItemController.class);
	private final BillItemService service;
	private final GenericSyncService genericSyncService;
	private final BillItemRepository billItemRepository;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<BillItemDTO> payload) {
		log.info("Received bill items push for {} items for Tenant: {}", payload.size(),
				TenantContext.getCurrentTenant());
		return ResponseEntity.ok(genericSyncService.handlePushSync(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, BillItem.class), billItemRepository));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<BillItemDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), BillItemDTO.class));
	}
}
