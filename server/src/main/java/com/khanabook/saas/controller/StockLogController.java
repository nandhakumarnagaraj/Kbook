package com.khanabook.saas.controller;

import com.khanabook.saas.entity.StockLog;
import com.khanabook.saas.repository.StockLogRepository;
import com.khanabook.saas.service.StockLogService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.security.TenantContext;

@RestController
@RequestMapping("/sync/stocklog")
@RequiredArgsConstructor
public class StockLogController {
	private final StockLogService service;
	private final GenericSyncService genericSyncService;
	private final StockLogRepository stockLogRepository;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<StockLogDTO> payload) {
		return ResponseEntity.ok(genericSyncService.handlePushSync(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, StockLog.class), stockLogRepository));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<StockLogDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), StockLogDTO.class));
	}
}
