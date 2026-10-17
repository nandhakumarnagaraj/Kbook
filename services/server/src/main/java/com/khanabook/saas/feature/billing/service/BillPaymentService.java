package com.khanabook.saas.feature.billing.service;

import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import java.util.List;

public interface BillPaymentService {

	PushSyncResponse pushData(Long tenantId, List<BillPayment> payload);

	List<BillPayment> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	org.springframework.data.domain.Page<BillPayment> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, org.springframework.data.domain.Pageable pageable);
}
