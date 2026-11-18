package com.khanabook.saas.sync.service;

import com.khanabook.saas.feature.sync.service.GenericSyncService;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BillPaymentIdempotentUpsertTest {

	private static final long TENANT = 9300L;
	private static final String DEVICE = "device-payment-test";

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long-for-hmac");
		registry.add("google.client.id", () -> "test-google-id");
		registry.add("kbook.cdn.base-path", () -> System.getProperty("java.io.tmpdir") + "/cdn-test");
		registry.add("terminal.sync.strict", () -> "false");
	}

	@Autowired
	private GenericSyncService syncService;

	@Autowired
	private BillRepository billRepository;

	@Autowired
	private BillPaymentRepository billPaymentRepository;

	private Long persistedBillId;

	@BeforeEach
	void setup() {
		TenantContext.setCurrentTenant(TENANT);
		TenantContext.setCurrentRole("OWNER");

		Bill bill = new Bill();
		bill.setLocalId(1L);
		bill.setDeviceId(DEVICE);
		bill.setRestaurantId(TENANT);
		bill.setPublicToken(UUID.randomUUID());
		bill.setDailyOrderId(1L);
		bill.setDailyOrderDisplay("T1-001");
		bill.setOrderType("dine_in");
		bill.setSubtotal(new BigDecimal("150.00"));
		bill.setTotalAmount(new BigDecimal("150.00"));
		bill.setPaymentMode("upi");
		bill.setPaymentStatus("success");
		bill.setOrderStatus("completed");
		bill.setLastResetDate("2026-11-18");
		bill.setUpdatedAt(System.currentTimeMillis());
		bill.setCreatedAt(System.currentTimeMillis());
		bill.setRefundAmount(BigDecimal.ZERO);

		PushSyncResponse billResp = syncService.handlePushSync(TENANT, List.of(bill), billRepository);
		persistedBillId = billResp.getLocalToServerIdMap().get(1L);
		assertThat(persistedBillId).isNotNull();
	}

	@AfterEach
	void teardown() {
		TenantContext.clear();
	}

	private BillPayment createTestPayment(Long localId, Long billId, String mode, BigDecimal amount, String operationId, long updatedAt) {
		BillPayment payment = new BillPayment();
		payment.setLocalId(localId);
		payment.setDeviceId(DEVICE);
		payment.setRestaurantId(TENANT);
		payment.setBillId(billId);
		payment.setServerBillId(billId);
		payment.setPaymentMode(mode);
		payment.setAmount(amount);
		payment.setOperationId(operationId);
		payment.setVerifiedBy("manual");
		payment.setUpdatedAt(updatedAt);
		payment.setCreatedAt(updatedAt);
		payment.setIsDeleted(false);
		return payment;
	}

	@Test
	@DisplayName("Exact retry with same localId and operationId succeeds without duplicating row")
	void duplicatePushReturnsIdempotentSuccess() {
		String opId = TENANT + ":null:" + UUID.randomUUID() + ":finalize:payment:upi";
		long now = System.currentTimeMillis();

		BillPayment first = createTestPayment(10L, persistedBillId, "upi", new BigDecimal("150.00"), opId, now);
		PushSyncResponse firstResp = syncService.handlePushSync(TENANT, List.of(first), billPaymentRepository);

		assertThat(firstResp.getSuccessfulLocalIds()).contains(10L);
		Long serverId = firstResp.getLocalToServerIdMap().get(10L);
		assertThat(serverId).isNotNull();

		// Second push: exact retry
		BillPayment retry = createTestPayment(10L, persistedBillId, "upi", new BigDecimal("150.00"), opId, now);
		PushSyncResponse retryResp = syncService.handlePushSync(TENANT, List.of(retry), billPaymentRepository);

		assertThat(retryResp.getSuccessfulLocalIds()).contains(10L);
		assertThat(retryResp.getFailedLocalIds()).doesNotContain(10L);
		assertThat(retryResp.getLocalToServerIdMap().get(10L)).isEqualTo(serverId);

		// Exactly 1 row in DB
		List<BillPayment> rows = billPaymentRepository.findByRestaurantIdAndServerBillIdIn(TENANT, List.of(persistedBillId));
		assertThat(rows).hasSize(1);
	}

	@Test
	@DisplayName("Payment update with different client localId but same operationId updates existing row and acknowledges client localId")
	void paymentUpdateWithDifferentLocalIdUpdatesExistingRowSafely() {
		String opId = TENANT + ":null:" + UUID.randomUUID() + ":finalize:payment:upi";
		long t1 = System.currentTimeMillis() - 10000;
		long t2 = System.currentTimeMillis();

		// Initial payment pushed with localId = 29, mode = upi
		BillPayment initial = createTestPayment(29L, persistedBillId, "upi", new BigDecimal("150.00"), opId, t1);
		PushSyncResponse firstResp = syncService.handlePushSync(TENANT, List.of(initial), billPaymentRepository);
		assertThat(firstResp.getSuccessfulLocalIds()).contains(29L);
		Long initialServerId = firstResp.getLocalToServerIdMap().get(29L);
		assertThat(initialServerId).isNotNull();

		// Also create an unrelated payment with localId = 15 to simulate row 1561 collision
		BillPayment unrelated = createTestPayment(15L, persistedBillId, "cash", new BigDecimal("500.00"), "unrelated:op:1", t1);
		PushSyncResponse unrelatedResp = syncService.handlePushSync(TENANT, List.of(unrelated), billPaymentRepository);
		assertThat(unrelatedResp.getSuccessfulLocalIds()).contains(15L);

		// Now client pushes update for the first payment, but sending localId = 15!
		// Mode changed from upi to cash, newer updatedAt.
		BillPayment updated = createTestPayment(15L, persistedBillId, "cash", new BigDecimal("150.00"), opId, t2);
		PushSyncResponse updateResp = syncService.handlePushSync(TENANT, List.of(updated), billPaymentRepository);

		// Must succeed for client localId 15
		assertThat(updateResp.getSuccessfulLocalIds()).contains(15L);
		assertThat(updateResp.getFailedLocalIds()).isEmpty();
		assertThat(updateResp.getLocalToServerIdMap().get(15L)).isEqualTo(initialServerId);

		// Verify row in DB was updated in-place to cash and kept its original server identity
		BillPayment inDb = billPaymentRepository.findById(initialServerId).orElseThrow();
		assertThat(inDb.getPaymentMode()).isEqualTo("cash");
		assertThat(inDb.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
		assertThat(inDb.getOperationId()).isEqualTo(opId);
	}

	@Test
	@DisplayName("Idempotent retry with different client localId acknowledges client localId")
	void idempotentRetryWithDifferentLocalIdAcknowledgesClient() {
		String opId = TENANT + ":null:" + UUID.randomUUID() + ":finalize:payment:cash";
		long now = System.currentTimeMillis();

		BillPayment initial = createTestPayment(50L, persistedBillId, "cash", new BigDecimal("100.00"), opId, now);
		PushSyncResponse firstResp = syncService.handlePushSync(TENANT, List.of(initial), billPaymentRepository);
		assertThat(firstResp.getSuccessfulLocalIds()).contains(50L);
		Long serverId = firstResp.getLocalToServerIdMap().get(50L);

		// Client pushes exact retry with localId = 22 and older timestamp
		BillPayment retry = createTestPayment(22L, persistedBillId, "cash", new BigDecimal("100.00"), opId, now - 1000);
		PushSyncResponse retryResp = syncService.handlePushSync(TENANT, List.of(retry), billPaymentRepository);

		assertThat(retryResp.getSuccessfulLocalIds()).contains(22L);
		assertThat(retryResp.getFailedLocalIds()).isEmpty();
		assertThat(retryResp.getLocalToServerIdMap().get(22L)).isEqualTo(serverId);
	}
}
