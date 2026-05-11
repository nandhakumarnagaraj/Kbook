package com.khanabook.saas.billing.service.impl;

import com.khanabook.saas.billing.domain.Bill;
import com.khanabook.saas.billing.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.billing.service.BillService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {
	private static final Logger log = LoggerFactory.getLogger(BillServiceImpl.class);
	private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.02");

	private final BillRepository repository;
	private final GenericSyncService genericSyncService;
	private final RestaurantProfileRepository restaurantProfileRepository;

	@Override
	public PushSyncResponse pushData(Long tenantId, List<Bill> payload) {
		ZoneId zoneId = restaurantProfileRepository.findByRestaurantId(tenantId)
				.map(profile -> resolveZoneId(profile.getTimezone()))
				.orElse(ZoneId.of("Asia/Kolkata"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zoneId);

		List<Long> failedLocalIds = new java.util.ArrayList<>();
		List<Bill> validatedPayload = new java.util.ArrayList<>();

		for (Bill bill : payload) {
			if (bill.getLastResetDate() == null) {
				Long created = bill.getCreatedAt() != null
						? bill.getCreatedAt()
						: bill.getUpdatedAt() != null ? bill.getUpdatedAt() : System.currentTimeMillis();
				bill.setLastResetDate(formatter.format(Instant.ofEpochMilli(created)));
			}

			if (!validateBillTotals(bill)) {
				log.warn("Bill validation failed for restaurant={}, localId={}: totals mismatch", tenantId, bill.getLocalId());
				failedLocalIds.add(bill.getLocalId());
			} else {
				validatedPayload.add(bill);
			}
		}

		PushSyncResponse response = genericSyncService.handlePushSync(tenantId, validatedPayload, repository);
		response.getFailedLocalIds().addAll(failedLocalIds);
		return response;
	}

	private boolean validateBillTotals(Bill bill) {
		if (bill.getSubtotal() == null || bill.getTotalAmount() == null) {
			return true;
		}

		if (bill.getGstPercentage() != null && bill.getGstPercentage().compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal cgst = bill.getCgstAmount() != null ? bill.getCgstAmount() : BigDecimal.ZERO;
			BigDecimal sgst = bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO;
			BigDecimal expectedGst = bill.getSubtotal()
					.multiply(bill.getGstPercentage())
					.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
			BigDecimal actualGst = cgst.add(sgst);
			if (expectedGst.subtract(actualGst).abs().compareTo(ROUNDING_TOLERANCE) > 0) {
				log.warn("GST mismatch: expected={}, actual={}, percentage={} for bill localId={}",
						expectedGst, actualGst, bill.getGstPercentage(), bill.getLocalId());
				return false;
			}
		}

		BigDecimal expectedTotal = bill.getSubtotal()
				.add(bill.getCgstAmount() != null ? bill.getCgstAmount() : BigDecimal.ZERO)
				.add(bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO)
				.add(bill.getCustomTaxAmount() != null ? bill.getCustomTaxAmount() : BigDecimal.ZERO);
		if (expectedTotal.subtract(bill.getTotalAmount()).abs().compareTo(ROUNDING_TOLERANCE) > 0) {
			log.warn("Total amount mismatch: expected={}, actual={} for bill localId={}",
					expectedTotal, bill.getTotalAmount(), bill.getLocalId());
			return false;
		}

		return true;
	}

	private ZoneId resolveZoneId(String timezone) {
		if (timezone == null || timezone.isBlank()) {
			return ZoneId.of("Asia/Kolkata");
		}
		try {
			return ZoneId.of(timezone);
		} catch (RuntimeException ignored) {
			return ZoneId.of("Asia/Kolkata");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<Bill> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp);
		}
		// Exclude own-device bills to avoid re-downloading what the device already created,
		// BUT include own-device deleted bills so server-side soft-deletes propagate back.
		return repository.findUpdatedExcludingOwnActiveOnly(tenantId, lastSyncTimestamp, deviceId);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Bill> pullDataPaginated(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, Pageable pageable) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp, pageable);
		}
		return repository.findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(
				tenantId, lastSyncTimestamp, deviceId, pageable);
	}
}
