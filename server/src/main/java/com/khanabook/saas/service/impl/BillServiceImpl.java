package com.khanabook.saas.service.impl;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.service.BillService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {
	private final BillRepository repository;
	private final GenericSyncService genericSyncService;
	private final RestaurantProfileRepository restaurantProfileRepository;
	private final RestaurantTerminalRepository terminalRepository;

	@Override
	@Transactional
	public PushSyncResponse pushData(Long tenantId, List<Bill> payload) {
		ZoneId zoneId = restaurantProfileRepository.findByRestaurantId(tenantId)
				.map(profile -> resolveZoneId(profile.getTimezone()))
				.orElse(ZoneId.of("Asia/Kolkata"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zoneId);

		for (Bill bill : payload) {
			if (bill.getLastResetDate() == null) {

				Long created = bill.getCreatedAt() != null
						? bill.getCreatedAt()
						: bill.getUpdatedAt() != null ? bill.getUpdatedAt() : System.currentTimeMillis();
				bill.setLastResetDate(formatter.format(Instant.ofEpochMilli(created)));
			}
		}
		allocateMissingInvoiceNumbers(tenantId, payload, zoneId);
		return genericSyncService.handlePushSync(tenantId, payload, repository);
	}

	private void allocateMissingInvoiceNumbers(Long tenantId, List<Bill> payload, ZoneId zoneId) {
		Map<String, Long> nextBySeries = new HashMap<>();
		for (Bill bill : payload) {
			if (bill.getInvoiceNumber() != null && !bill.getInvoiceNumber().isBlank()) {
				continue;
			}
			String terminalSeries = bill.getTerminalSeries();
			if (terminalSeries == null || terminalSeries.isBlank()) {
				continue;
			}
			if (terminalRepository.findAndLockByRestaurantIdAndTerminalSeries(tenantId, terminalSeries).isEmpty()) {
				continue;
			}

			long createdAt = bill.getCreatedAt() != null ? bill.getCreatedAt() : System.currentTimeMillis();
			LocalDate issueDate = Instant.ofEpochMilli(createdAt).atZone(zoneId).toLocalDate();
			int financialYearStart = issueDate.getMonthValue() >= 4 ? issueDate.getYear() : issueDate.getYear() - 1;
			String financialYear = String.format("%02d", financialYearStart % 100);
			String key = terminalSeries + "|" + financialYear;
			long sequence = nextBySeries.computeIfAbsent(key, ignored ->
					repository.findMaxInvoiceSequence(tenantId, terminalSeries, financialYear) + 1L);
			String invoiceSeries = financialYear + terminalSeries;
			bill.setFinancialYear(financialYear);
			bill.setInvoiceSeries(invoiceSeries);
			bill.setInvoiceSequence(sequence);
			bill.setInvoiceNumber(invoiceSeries + "-" + String.format("%06d", sequence));
			nextBySeries.put(key, sequence + 1L);
		}
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
	public org.springframework.data.domain.Page<Bill> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId, org.springframework.data.domain.Pageable pageable) {
		if (ignoreDeviceId) {
			return repository.findByRestaurantIdAndServerUpdatedAtGreaterThan(tenantId, lastSyncTimestamp, pageable);
		}
		return repository.findUpdatedExcludingOwnActiveOnly(tenantId, lastSyncTimestamp, deviceId, pageable);
	}
}
