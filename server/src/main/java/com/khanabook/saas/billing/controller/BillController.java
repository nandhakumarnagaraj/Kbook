package com.khanabook.saas.billing.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.khanabook.saas.billing.domain.Bill;
import com.khanabook.saas.billing.repository.BillRepository;
import com.khanabook.saas.billing.service.BillService;
import com.khanabook.saas.common.TenantContext;
import com.khanabook.saas.sync.dto.PullSyncResponse;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.BillDTO;
import com.khanabook.saas.sync.dto.payload.SyncMapper;
import com.khanabook.saas.sync.service.GenericSyncService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sync/bills")
@RequiredArgsConstructor
public class BillController {
	private final BillService service;
	private final GenericSyncService genericSyncService;
	private final BillRepository billRepository;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<BillDTO> payload) {
		return ResponseEntity.ok(genericSyncService.handlePushSync(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, Bill.class), billRepository));
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
			Page<Bill> billPage = service.pullDataPaginated(
					TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId, pageRequest);
			return ResponseEntity.ok(PullSyncResponse.fromPage(
					billPage.map(bill -> SyncMapper.map(bill, BillDTO.class))));
		}

		return ResponseEntity.ok(SyncMapper.mapList(
				service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId),
				BillDTO.class));
	}
}
