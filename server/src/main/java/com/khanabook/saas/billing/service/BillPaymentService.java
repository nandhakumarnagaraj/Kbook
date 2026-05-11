package com.khanabook.saas.billing.service;

import com.khanabook.saas.billing.domain.BillPayment;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BillPaymentService {

	PushSyncResponse pushData(Long tenantId, List<BillPayment> payload);

	List<BillPayment> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	Page<BillPayment> pullDataPaginated(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, Pageable pageable);
}
