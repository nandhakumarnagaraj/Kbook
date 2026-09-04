package com.khanabook.saas.controller;

import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.service.BillPaymentService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.khanabook.saas.security.TenantContext;

import com.khanabook.saas.sync.validation.SyncPushGuard;

@RestController
@RequestMapping("/sync/bills/payments")
@RequiredArgsConstructor
public class BillPaymentController {
    private final BillPaymentService service;
    private final com.khanabook.saas.service.PermissionService permissionService;

    @PostMapping("/push")
    public ResponseEntity<PushSyncResponse> push(@RequestBody List<BillPaymentDTO> payload) {
        SyncPushGuard.validateBatchSize(payload);
        SyncPushGuard.requirePermission(
                com.khanabook.saas.entity.PermissionKey.BILLING_SETTLE.getKey(), permissionService);
        return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
                SyncMapper.mapToEntityList(payload, BillPayment.class)));
    }

    @GetMapping("/pull")
    public ResponseEntity<List<BillPaymentDTO>> pull(
            @RequestParam Long lastSyncTimestamp,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
        return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), BillPaymentDTO.class));
    }
}
