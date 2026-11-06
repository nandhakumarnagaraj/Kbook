package com.khanabook.saas.feature.shifts.service;

import com.khanabook.saas.feature.shifts.data.CashierShift;
import com.khanabook.saas.feature.shifts.data.CashierShiftRepository;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CashierShiftServiceImpl implements CashierShiftService {

    private final CashierShiftRepository repository;
    private final GenericSyncService genericSyncService;

    @Override
    public PushSyncResponse pushData(Long tenantId, List<CashierShift> payload) {
        return genericSyncService.handlePushSync(tenantId, payload, repository);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashierShift> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
        if (ignoreDeviceId) {
            return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
        }
        return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(
                tenantId, lastSyncTimestamp, deviceId);
    }
}
