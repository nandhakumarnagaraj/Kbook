package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.BillPaymentService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class BillPaymentServiceImpl implements BillPaymentService {
	private final BillPaymentRepository repository;
	private final BillRepository billRepository;
	private final GenericSyncService genericSyncService;

	// Phase C strict mode: reject child pushes without an X-Terminal-Token.
	@Value("${terminal.sync.strict:false}")
	private boolean terminalSyncStrict;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<BillPayment> payload) {
		enforceTerminalIdentity();
		List<BillPayment> toSync = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();
		Map<Long, String> failedReasons = new HashMap<>();

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
				if (payment.getServerBillId() == null) {
					billRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(payment.getBillId()))
							.stream().findFirst()
							.ifPresent(b -> payment.setServerBillId(b.getId()));
				}
			}

			if (payment.getServerBillId() == null) {
				addFailure(failedLocalIds, failedReasons, payment.getLocalId(),
						"Bill payment parent bill could not be resolved");
				resolved = false;
			}

			if (resolved) {
				toSync.add(payment);
			}
		}
		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, toSync, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
		response.getFailedReasons().putAll(failedReasons);
		return response;
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
	public org.springframework.data.domain.Page<BillPayment> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, org.springframework.data.domain.Pageable pageable) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp, pageable);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(tenantId, lastSyncTimestamp,
				deviceId, pageable);
	}

	private void enforceTerminalIdentity() {
		boolean isAdmin = "KBOOK_ADMIN".equals(TenantContext.getCurrentRole());
		if (terminalSyncStrict && !isAdmin && TenantContext.getCurrentTerminalId() == null) {
			throw new ResponseStatusException(BAD_REQUEST,
					"Terminal identity required for sync: activate a terminal and send X-Terminal-Token");
		}
	}

	private void addFailure(List<Long> failedLocalIds, Map<Long, String> failedReasons, Long localId, String reason) {
		if (localId == null) {
			return;
		}
		if (!failedLocalIds.contains(localId)) {
			failedLocalIds.add(localId);
		}
		failedReasons.put(localId, reason);
	}
}
