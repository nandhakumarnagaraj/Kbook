package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import java.util.List;

public interface BillService {

	PushSyncResponse pushData(Long tenantId, List<Bill> payload);

	List<Bill> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);
}
