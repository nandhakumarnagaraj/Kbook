package com.khanabook.saas.feature.inventory.service;

import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import java.util.List;

public interface StockLogService {

	PushSyncResponse pushData(Long tenantId, List<StockLog> payload);

	List<StockLog> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	org.springframework.data.domain.Page<StockLog> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, org.springframework.data.domain.Pageable pageable);
}
