package com.khanabook.saas.billing.service.impl;

import com.khanabook.saas.billing.domain.Bill;
import com.khanabook.saas.billing.domain.BillPayment;
import com.khanabook.saas.billing.repository.BillRepository;
import com.khanabook.saas.billing.repository.BillPaymentRepository;
import com.khanabook.saas.billing.service.BillPaymentService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import com.khanabook.saas.common.BatchQueryUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillPaymentServiceImpl implements BillPaymentService {
	private final BillPaymentRepository repository;
	private final BillRepository billRepository;
	private final GenericSyncService genericSyncService;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<BillPayment> payload) {
		Set<Long> billLocalIds = new HashSet<>();
		Set<String> deviceIds = new HashSet<>();

		for (BillPayment payment : payload) {
			if (payment.getServerBillId() == null && payment.getBillId() != null) {
				billLocalIds.add(payment.getBillId());
			}
			if (payment.getDeviceId() != null) {
				deviceIds.add(payment.getDeviceId());
			}
		}

		Map<Long, Long> billLocalToServerId = resolveBillIds(tenantId, billLocalIds, deviceIds);

		List<BillPayment> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();

		for (BillPayment payment : payload) {
			if (payment.getServerBillId() == null && payment.getBillId() != null) {
				payment.setServerBillId(billLocalToServerId.get(payment.getBillId()));
			}

			if (payment.getServerBillId() == null) {
				failedLocalIds.add(payment.getLocalId());
			} else {
				toSync.add(payment);
			}
		}
		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
		return response;
	}

	private Map<Long, Long> resolveBillIds(Long tenantId, Set<Long> localIds, Set<String> deviceIds) {
		Map<Long, Long> result = new HashMap<>();
		if (localIds.isEmpty()) return result;
		for (String deviceId : deviceIds) {
			String dev = deviceId;
			List<Bill> bills = BatchQueryUtil.queryInBatches(localIds, batch ->
					billRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, dev, batch));
			for (Bill bill : bills) {
				result.put(bill.getLocalId(), bill.getId());
			}
		}
		List<Bill> fallbackBills = BatchQueryUtil.queryInBatches(localIds, batch ->
				billRepository.findByRestaurantIdAndLocalIdIn(tenantId, batch));
		for (Bill bill : fallbackBills) {
			result.putIfAbsent(bill.getLocalId(), bill.getId());
		}
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public List<BillPayment> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<BillPayment> pullDataPaginated(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, Pageable pageable) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp, pageable);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(
				tenantId, lastSyncTimestamp, deviceId, pageable);
	}
}
