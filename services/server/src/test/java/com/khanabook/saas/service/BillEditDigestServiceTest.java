package com.khanabook.saas.service;

import com.khanabook.saas.feature.billing.service.BillEditDigestService;
import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.auth.entity.SecurityAuditEvent;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.auth.repository.SecurityAuditLogRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;

/**
 * Unit tests for {@link BillEditDigestService} — the owner's once-a-day summary of bill
 * edits that moved recorded revenue (voids and payment-mode switches).
 */
class BillEditDigestServiceTest {

	private static final Long TENANT = 1L;
	private static final LocalDate DAY = LocalDate.of(2026, 9, 14);

	private SecurityAuditLogRepository auditRepository;
	private RestaurantProfileRepository profileRepository;
	private UserRepository userRepository;
	private PushNotificationService pushNotificationService;
	private BillEditDigestService service;

	@BeforeEach
	void setUp() {
		auditRepository = mock(SecurityAuditLogRepository.class);
		profileRepository = mock(RestaurantProfileRepository.class);
		userRepository = mock(UserRepository.class);
		pushNotificationService = mock(PushNotificationService.class);
		service = new BillEditDigestService(auditRepository, profileRepository,
				userRepository, pushNotificationService);

		RestaurantProfile profile = new RestaurantProfile();
		profile.setRestaurantId(TENANT);
		when(profileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
				.thenReturn(List.of(profile));

		User owner = new User();
		owner.setId(42L);
		owner.setRole(UserRole.OWNER);
		when(userRepository.findByRestaurantIdAndRoleAndIsDeletedFalse(TENANT, UserRole.OWNER))
				.thenReturn(List.of(owner));
	}

	@Test
	@DisplayName("no audit events means no notification at all")
	void quietDay_sendsNothing() {
		when(auditRepository.findForDigest(eq(TENANT), anyList(), anyLong(), anyLong()))
				.thenReturn(List.of());

		service.sendDigestForDate(DAY);

		// Silence is the common case — a daily "nothing happened" push trains owners
		// to ignore the channel.
		verify(pushNotificationService, never())
				.pushToUsers(anyLong(), anyList(), anyString(), anyString(),
						anyString(), anyString(), anyString(), any());
	}

	@Test
	@DisplayName("device-pushed cancellation counts as a void")
	void deviceCancellation_countsAsVoid() {
		when(auditRepository.findForDigest(eq(TENANT), anyList(), anyLong(), anyLong()))
				.thenReturn(List.of(event("BILL_STATUS_CHANGE", "completed->cancelled")));

		service.sendDigestForDate(DAY);

		assertMessageContains("1 bill cancelled");
	}

	@Test
	@DisplayName("web-admin void counts as a void")
	void webAdminVoid_countsAsVoid() {
		when(auditRepository.findForDigest(eq(TENANT), anyList(), anyLong(), anyLong()))
				.thenReturn(List.of(event("BILL_VOID", "SUCCESS")));

		service.sendDigestForDate(DAY);

		assertMessageContains("1 bill cancelled");
	}

	@Test
	@DisplayName("payment mode changes are reported separately from cancellations")
	void payModeChange_reportedSeparately() {
		when(auditRepository.findForDigest(eq(TENANT), anyList(), anyLong(), anyLong()))
				.thenReturn(List.of(
						event("BILL_STATUS_CHANGE", "completed->cancelled"),
						event("BILL_STATUS_CHANGE", "draft->cancelled"),
						event("BILL_PAYMODE_CHANGE", "upi->cash")));

		service.sendDigestForDate(DAY);

		String message = capturedMessage();
		org.junit.jupiter.api.Assertions.assertTrue(message.contains("2 bills cancelled"), message);
		org.junit.jupiter.api.Assertions.assertTrue(message.contains("1 payment mode changed"), message);
	}

	@Test
	@DisplayName("a non-cancelling status change is not counted as a void")
	void nonCancellingStatusChange_isNotAVoid() {
		when(auditRepository.findForDigest(eq(TENANT), anyList(), anyLong(), anyLong()))
				.thenReturn(List.of(event("BILL_STATUS_CHANGE", "draft->completed")));

		service.sendDigestForDate(DAY);

		String message = capturedMessage();
		org.junit.jupiter.api.Assertions.assertFalse(message.contains("cancelled"), message);
		org.junit.jupiter.api.Assertions.assertTrue(message.contains("1 other status change"), message);
	}

	@Test
	@DisplayName("no owner on the tenant means nothing is sent")
	void noOwner_sendsNothing() {
		when(auditRepository.findForDigest(eq(TENANT), anyList(), anyLong(), anyLong()))
				.thenReturn(List.of(event("BILL_VOID", "SUCCESS")));
		when(userRepository.findByRestaurantIdAndRoleAndIsDeletedFalse(TENANT, UserRole.OWNER))
				.thenReturn(List.of());

		service.sendDigestForDate(DAY);

		verify(pushNotificationService, never())
				.pushToUsers(anyLong(), anyList(), anyString(), anyString(),
						anyString(), anyString(), anyString(), any());
	}

	@Test
	@DisplayName("a delivery failure does not abort the scheduled run")
	void deliveryFailure_isSwallowed() {
		when(auditRepository.findForDigest(eq(TENANT), anyList(), anyLong(), anyLong()))
				.thenReturn(List.of(event("BILL_VOID", "SUCCESS")));
		org.mockito.Mockito.doThrow(new RuntimeException("FCM down"))
				.when(pushNotificationService)
				.pushToUsers(anyLong(), anyList(), anyString(), anyString(),
						anyString(), anyString(), anyString(), any());

		// A digest is advisory: one tenant's failed push must not stop the others.
		service.sendDigestForDate(DAY);
	}

	private void assertMessageContains(String fragment) {
		String message = capturedMessage();
		org.junit.jupiter.api.Assertions.assertTrue(message.contains(fragment),
				"expected message to contain \"" + fragment + "\" but was: " + message);
	}

	private String capturedMessage() {
		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		// The digest carries no amount, and Mockito's any(Class) excludes nulls —
		// isNull() is required here.
		verify(pushNotificationService).pushToUsers(eq(TENANT), anyList(), anyString(),
				message.capture(), anyString(), anyString(), anyString(),
				org.mockito.ArgumentMatchers.isNull());
		return message.getValue();
	}

	private SecurityAuditEvent event(String action, String outcome) {
		return new SecurityAuditEvent(TENANT, "7", "T-1", "A", "token", null,
				action, outcome, "req-1", System.currentTimeMillis());
	}
}
