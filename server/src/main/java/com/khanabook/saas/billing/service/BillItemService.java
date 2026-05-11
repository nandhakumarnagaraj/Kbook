package com.khanabook.saas.billing.service;

import com.khanabook.saas.billing.domain.BillItem;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BillItemService {
	
	PushSyncResponse pushData(Long tenantId, List<BillItem> payload);

	List<BillItem> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	Page<BillItem> pullDataPaginated(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, Pageable pageable);
}
