package com.khanabook.saas.billing.controller;

import com.khanabook.saas.billing.domain.BillItem;
import com.khanabook.saas.billing.repository.BillItemRepository;
import com.khanabook.saas.billing.service.BillItemService;
import com.khanabook.saas.sync.dto.PullSyncResponse;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.common.TenantContext;

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
	public ResponseEntity<?> pull(
			@RequestParam Long lastSyncTimestamp,
			@RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size) {

		if (page > 0 || size < 100) {
			PageRequest pageRequest = PageRequest.of(page, size, Sort.by("serverUpdatedAt").ascending());
			Page<BillItem> pageResult = service.pullDataPaginated(
					TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId, pageRequest);
			return ResponseEntity.ok(PullSyncResponse.fromPage(
					pageResult.map(item -> SyncMapper.map(item, BillItemDTO.class))));
		}

		return ResponseEntity.ok(SyncMapper.mapList(
				service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId),
				BillItemDTO.class));
	}
}
