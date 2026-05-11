package com.khanabook.saas.billing.service;

import com.khanabook.saas.billing.domain.Bill;
import com.khanabook.saas.sync.dto.PullSyncResponse;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BillService {

	PushSyncResponse pushData(Long tenantId, List<Bill> payload);

	List<Bill> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	Page<Bill> pullDataPaginated(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, Pageable pageable);
}
