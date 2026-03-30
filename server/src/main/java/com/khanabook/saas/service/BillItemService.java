package com.khanabook.saas.service;

import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import java.util.List;

public interface BillItemService {
	
	PushSyncResponse pushData(Long tenantId, List<BillItem> payload);

	List<BillItem> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);
}
