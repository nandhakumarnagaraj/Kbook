package com.khanabook.saas.feature.shifts.controller;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.shifts.data.CashierShift;
import com.khanabook.saas.feature.shifts.data.CashierShiftDTO;
import com.khanabook.saas.feature.shifts.service.CashierShiftService;
import com.khanabook.saas.feature.staff.data.PermissionKey;
import com.khanabook.saas.feature.staff.service.PermissionService;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.sync.data.SyncMapper;
import com.khanabook.saas.feature.sync.validation.SyncPushGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Shift sync endpoints.
 *
 * <p>The gate is the billing permission, not the master-data writer gate: closing a drawer
 * is a cashier action. There is no separate shift grant yet; if one is added, swap the key
 * here and nothing else changes.
 */
@RestController
@RequestMapping("/sync/shifts")
@RequiredArgsConstructor
public class CashierShiftController {

    private final CashierShiftService service;
    private final PermissionService permissionService;

    @PostMapping("/push")
    public ResponseEntity<PushSyncResponse> push(@RequestBody List<CashierShiftDTO> payload) {
        SyncPushGuard.validateBatchSize(payload);
        SyncPushGuard.requirePermission(PermissionKey.BILLING_CREATE.getKey(), permissionService);
        return ResponseEntity.ok(service.pushData(TenantContext.getCurrentTenant(),
                SyncMapper.mapToEntityList(payload, CashierShift.class)));
    }

    @GetMapping("/pull")
    public ResponseEntity<List<CashierShiftDTO>> pull(
            @RequestParam Long lastSyncTimestamp,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
        return ResponseEntity.ok(SyncMapper.mapList(
                service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId),
                CashierShiftDTO.class));
    }
}
