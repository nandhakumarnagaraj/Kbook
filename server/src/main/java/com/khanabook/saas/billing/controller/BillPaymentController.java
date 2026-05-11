package com.khanabook.saas.billing.controller;

import com.khanabook.saas.billing.domain.BillPayment;
import com.khanabook.saas.billing.repository.BillPaymentRepository;
import com.khanabook.saas.billing.service.BillPaymentService;
import com.khanabook.saas.sync.dto.PullSyncResponse;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.common.TenantContext;

@RestController
@RequestMapping("/sync/bills/payments")
@RequiredArgsConstructor
public class BillPaymentController {
    private final BillPaymentService service;
    private final GenericSyncService genericSyncService;
    private final BillPaymentRepository billPaymentRepository;

    @PostMapping("/push")
    public ResponseEntity<PushSyncResponse> push(@RequestBody List<BillPaymentDTO> payload) {
        return ResponseEntity.ok(genericSyncService.handlePushSync(TenantContext.getCurrentTenant(),
                SyncMapper.mapToEntityList(payload, BillPayment.class), billPaymentRepository));
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
            Page<BillPayment> pageResult = service.pullDataPaginated(
                    TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId, pageRequest);
            return ResponseEntity.ok(PullSyncResponse.fromPage(
                    pageResult.map(p -> SyncMapper.map(p, BillPaymentDTO.class))));
        }

        return ResponseEntity.ok(SyncMapper.mapList(
                service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId),
                BillPaymentDTO.class));
    }
}
