package com.khanabook.saas.controller;

import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.service.BillPaymentService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.security.TenantContext;

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
    public ResponseEntity<List<BillPaymentDTO>> pull(
            @RequestParam Long lastSyncTimestamp,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
        return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), BillPaymentDTO.class));
    }
}
