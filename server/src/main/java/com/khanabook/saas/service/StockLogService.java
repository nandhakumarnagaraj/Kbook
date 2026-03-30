package com.khanabook.saas.service;

import com.khanabook.saas.entity.StockLog;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import java.util.List;

public interface StockLogService {

	PushSyncResponse pushData(Long tenantId, List<StockLog> payload);

	List<StockLog> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);
}
