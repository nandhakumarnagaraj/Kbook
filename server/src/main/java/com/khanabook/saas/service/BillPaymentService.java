package com.khanabook.saas.service;

import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import java.util.List;

public interface BillPaymentService {

	PushSyncResponse pushData(Long tenantId, List<BillPayment> payload);

	List<BillPayment> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);
}
