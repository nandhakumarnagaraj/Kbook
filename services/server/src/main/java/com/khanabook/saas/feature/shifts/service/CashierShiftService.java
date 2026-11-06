package com.khanabook.saas.feature.shifts.service;

import com.khanabook.saas.feature.shifts.data.CashierShift;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;

import java.util.List;

public interface CashierShiftService {

    PushSyncResponse pushData(Long tenantId, List<CashierShift> payload);

    List<CashierShift> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);
}
