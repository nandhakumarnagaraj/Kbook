package com.khanabook.saas.feature.billing.service;

import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import java.util.List;

public interface BillItemService {
	
	PushSyncResponse pushData(Long tenantId, List<BillItem> payload);

	List<BillItem> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	org.springframework.data.domain.Page<BillItem> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, org.springframework.data.domain.Pageable pageable);
}
