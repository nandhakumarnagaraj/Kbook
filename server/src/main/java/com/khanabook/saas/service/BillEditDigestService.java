package com.khanabook.saas.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.SecurityAuditEvent;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.SecurityAuditLogRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.core.utility.AppConstants;

/**
 * Sends the restaurant owner a once-a-day summary of bill edits that moved recorded
 * revenue without changing any line item: voids/cancellations and payment-mode switches.
 *
 * <p>Rationale (product decision): the POS deliberately lets staff void bills and change
 * payment mode without asking for approval, because an approval round trip would block a
 * sale with a customer at the counter and would fail exactly when connectivity is worst.
 * Visibility after the fact replaces prevention up front — the owner sees the pattern
 * (a staffer voiding several bills a week) without anyone waiting on them.
 *
 * <p>Reads only the append-only audit log written by
 * {@code GenericSyncService.auditBillEdits} and {@code BusinessReadService.voidBill}.
 */
@Service
public class BillEditDigestService {

	private static final Logger log = LoggerFactory.getLogger(BillEditDigestService.class);

	/** Audit actions that represent a money-affecting bill edit. */
	private static final List<String> DIGEST_ACTIONS =
			List.of("BILL_STATUS_CHANGE", "BILL_PAYMODE_CHANGE", "BILL_VOID");

	private static final ZoneId ZONE = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);

	private final SecurityAuditLogRepository auditRepository;
	private final RestaurantProfileRepository restaurantProfileRepository;
	private final UserRepository userRepository;
	private final PushNotificationService pushNotificationService;

	public BillEditDigestService(SecurityAuditLogRepository auditRepository,
			RestaurantProfileRepository restaurantProfileRepository,
			UserRepository userRepository,
			PushNotificationService pushNotificationService) {
		this.auditRepository = auditRepository;
		this.restaurantProfileRepository = restaurantProfileRepository;
		this.userRepository = userRepository;
		this.pushNotificationService = pushNotificationService;
	}

	/** Runs at 09:00 IST and reports on the previous business day. */
	@Scheduled(cron = "0 0 9 * * *", zone = AppConstants.DEFAULT_TIMEZONE)
	@Transactional(readOnly = true)
	public void runDailyDigest() {
		sendDigestForDate(LocalDate.now(ZONE).minusDays(1));
	}

	/**
	 * Sends the digest for one calendar day (IST) to every restaurant's owners.
	 * Package-visible for tests and manual replay.
	 */
	@Transactional(readOnly = true)
	public void sendDigestForDate(LocalDate day) {
		long fromMillis = day.atStartOfDay(ZONE).toInstant().toEpochMilli();
		long toMillis = day.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();

		List<RestaurantProfile> profiles =
				restaurantProfileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
		int sent = 0;
		for (RestaurantProfile profile : profiles) {
			if (sendDigestForRestaurant(profile.getRestaurantId(), day, fromMillis, toMillis)) {
				sent++;
			}
		}
		log.info("Bill edit digest for {}: notified {} of {} restaurants", day, sent, profiles.size());
	}

	private boolean sendDigestForRestaurant(Long restaurantId, LocalDate day,
			long fromMillis, long toMillis) {
		if (restaurantId == null) return false;

		List<SecurityAuditEvent> events =
				auditRepository.findForDigest(restaurantId, DIGEST_ACTIONS, fromMillis, toMillis);
		// Silence is the common case — never send an empty digest.
		if (events.isEmpty()) return false;

		List<Long> ownerIds = userRepository
				.findByRestaurantIdAndRoleAndIsDeletedFalse(restaurantId, UserRole.OWNER)
				.stream()
				.map(User::getId)
				.collect(Collectors.toList());
		if (ownerIds.isEmpty()) {
			log.debug("Bill edit digest: no owner to notify for restaurantId={}", restaurantId);
			return false;
		}

		long voids = events.stream().filter(e -> isVoid(e)).count();
		long payModeChanges = events.stream()
				.filter(e -> "BILL_PAYMODE_CHANGE".equals(e.getAction()))
				.count();
		long otherStatusChanges = events.size() - voids - payModeChanges;

		String message = buildMessage(voids, payModeChanges, otherStatusChanges);
		try {
			pushNotificationService.pushToUsers(restaurantId, ownerIds,
					"Yesterday's bill edits", message,
					"BILL_EDIT_DIGEST", day.toString(), "DIGEST", null);
			return true;
		} catch (Exception e) {
			// A digest is advisory: never let a delivery failure break the scheduled run.
			log.warn("Bill edit digest delivery failed for restaurantId={}", restaurantId, e);
			return false;
		}
	}

	/**
	 * A cancellation shows up either as an explicit web-admin void or as a device-pushed
	 * status change whose new value is "cancelled" (outcome is recorded as "old->new").
	 */
	private boolean isVoid(SecurityAuditEvent event) {
		if ("BILL_VOID".equals(event.getAction())) return true;
		if (!"BILL_STATUS_CHANGE".equals(event.getAction())) return false;
		String outcome = event.getOutcome();
		return outcome != null && outcome.toLowerCase().endsWith("->cancelled");
	}

	private String buildMessage(long voids, long payModeChanges, long otherStatusChanges) {
		List<String> parts = new ArrayList<>();
		if (voids > 0) {
			parts.add(voids + (voids == 1 ? " bill cancelled" : " bills cancelled"));
		}
		if (payModeChanges > 0) {
			parts.add(payModeChanges + (payModeChanges == 1
					? " payment mode changed"
					: " payment modes changed"));
		}
		if (otherStatusChanges > 0) {
			parts.add(otherStatusChanges + (otherStatusChanges == 1
					? " other status change"
					: " other status changes"));
		}
		return String.join(", ", parts) + ". Open Orders to review.";
	}
}
