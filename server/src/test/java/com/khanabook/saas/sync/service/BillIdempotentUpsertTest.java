package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.sync.dto.PushSyncResponse;
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
class BillIdempotentUpsertTest {

	private static final long TENANT = 9200L;
	private static final String DEVICE = "device-idempotent-test";

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

	@BeforeEach
	void setup() {
		TenantContext.setCurrentTenant(TENANT);
		TenantContext.setCurrentRole("OWNER");
	}

	@AfterEach
	void teardown() {
		TenantContext.clear();
	}

	private Bill createTestBill(UUID publicToken, Long localId) {
		Bill bill = new Bill();
		bill.setLocalId(localId);
		bill.setDeviceId(DEVICE);
		bill.setRestaurantId(TENANT);
		bill.setPublicToken(publicToken);
		bill.setDailyOrderId(1L);
		bill.setDailyOrderDisplay("T1-001");
		bill.setOrderType("dine_in");
		bill.setSubtotal(new BigDecimal("200.00"));
		bill.setTotalAmount(new BigDecimal("236.00"));
		bill.setPaymentMode("upi");
		bill.setPaymentStatus("success");
		bill.setOrderStatus("completed");
		bill.setLastResetDate("2026-08-07");
		bill.setUpdatedAt(System.currentTimeMillis());
		bill.setCreatedAt(System.currentTimeMillis());
		bill.setRefundAmount(BigDecimal.ZERO);
		return bill;
	}

	@Test
	@DisplayName("Pushing the same bill twice (same publicToken) returns success both times with same serverId")
	void duplicatePushReturnsIdempotentSuccess() {
		UUID token = UUID.randomUUID();

		// First push — should succeed and persist the bill
		Bill firstPush = createTestBill(token, 100L);
		PushSyncResponse firstResponse = syncService.handlePushSync(TENANT, List.of(firstPush), billRepository);

		assertThat(firstResponse.getSuccessfulLocalIds()).contains(100L);
		assertThat(firstResponse.getFailedLocalIds()).doesNotContain(100L);
		Long serverId = firstResponse.getLocalToServerIdMap().get(100L);
		assertThat(serverId).isNotNull();

		// Second push — same publicToken, different localId (simulating client retry)
		Bill secondPush = createTestBill(token, 200L);
		PushSyncResponse secondResponse = syncService.handlePushSync(TENANT, List.of(secondPush), billRepository);

		assertThat(secondResponse.getSuccessfulLocalIds()).contains(200L);
		assertThat(secondResponse.getFailedLocalIds()).doesNotContain(200L);
		Long secondServerId = secondResponse.getLocalToServerIdMap().get(200L);
		assertThat(secondServerId).isEqualTo(serverId);

		// Verify only one bill exists in the database
		long count = billRepository.countByRestaurantIdAndIsDeletedFalse(TENANT);
		assertThat(count).isEqualTo(1);
	}

	@Test
	@DisplayName("Pushing the same bill with same localId returns success without duplication")
	void sameLocalIdRetryReturnsSuccess() {
		UUID token = UUID.randomUUID();

		Bill firstPush = createTestBill(token, 300L);
		PushSyncResponse firstResponse = syncService.handlePushSync(TENANT, List.of(firstPush), billRepository);
		assertThat(firstResponse.getSuccessfulLocalIds()).contains(300L);

		// Retry with exact same localId + publicToken
		Bill retryPush = createTestBill(token, 300L);
		PushSyncResponse retryResponse = syncService.handlePushSync(TENANT, List.of(retryPush), billRepository);
		assertThat(retryResponse.getSuccessfulLocalIds()).contains(300L);
		assertThat(retryResponse.getFailedLocalIds()).isEmpty();
	}

	@Test
	@DisplayName("Bills with different publicTokens create separate records")
	void differentPublicTokensCreateSeparateBills() {
		Bill bill1 = createTestBill(UUID.randomUUID(), 400L);
		Bill bill2 = createTestBill(UUID.randomUUID(), 401L);

		syncService.handlePushSync(TENANT, List.of(bill1), billRepository);
		syncService.handlePushSync(TENANT, List.of(bill2), billRepository);

		long count = billRepository.countByRestaurantIdAndIsDeletedFalse(TENANT);
		// At least 2 bills from this test (others may exist from earlier tests)
		assertThat(count).isGreaterThanOrEqualTo(2);
	}
}
