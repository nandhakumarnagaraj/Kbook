package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.service.BillPaymentService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import java.util.Optional;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillPaymentServiceImpl implements BillPaymentService {
	private final BillPaymentRepository repository;
	private final BillRepository billRepository;
	private final GenericSyncService genericSyncService;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<BillPayment> payload) {
		List<BillPayment> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();

		for (BillPayment payment : payload) {
			boolean resolved = true;
			if (payment.getServerBillId() == null && payment.getBillId() != null) {
				Optional<Bill> bill = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId,
						payment.getDeviceId(), payment.getBillId());
				if (bill.isPresent()) {
					payment.setServerBillId(bill.get().getId());
				} else {
					billRepository.findById(payment.getBillId())
							.filter(b -> b.getRestaurantId().equals(tenantId))
							.ifPresent(b -> payment.setServerBillId(b.getId()));
				}
			}

			if (payment.getServerBillId() == null) {
				failedLocalIds.add(payment.getLocalId());
				resolved = false;
			}

			if (resolved) {
				toSync.add(payment);
			}
		}
		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
		return response;
	}

	@Override
	public List<BillPayment> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId);
	}
}
