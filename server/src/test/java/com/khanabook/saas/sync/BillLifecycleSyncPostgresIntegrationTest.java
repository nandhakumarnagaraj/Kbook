package com.khanabook.saas.sync;

import com.khanabook.saas.controller.MasterSyncController;
import com.khanabook.saas.controller.TerminalController;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.BillItemService;
import com.khanabook.saas.service.BillPaymentService;
import com.khanabook.saas.service.BillService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.MasterSyncResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class BillLifecycleSyncPostgresIntegrationTest {

    private static final long RESTAURANT_ID = 9200L;
    private static final long USER_ID = 1L;
    private static final String DEVICE_ID = "bill-lifecycle-device";
    private static final long BILL_LOCAL_ID = 900001L;
    private static final long ITEM_1_LOCAL_ID = 900101L;
    private static final long ITEM_2_LOCAL_ID = 900102L;
    private static final long PAYMENT_LOCAL_ID = 900201L;
    private static final long FIXED_TIMESTAMP = Instant.parse("2026-07-28T09:00:00Z").toEpochMilli();
    private static final UUID FIXED_PUBLIC_TOKEN =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("kbook_bill_lifecycle_test")
                    .withUsername("kbook")
                    .withPassword("kbook");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("JWT_SECRET", () ->
                "bill-lifecycle-test-secret-64-chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-google-client-id");
        registry.add("PAYMENT_CRYPTO_SECRET", () ->
                "bill-lifecycle-payment-secret-32-bytes-min-xxxx");
        registry.add("APP_BASE_URL", () -> "https://test.khanabook.app");
    }

    @Autowired private BillService billService;
    @Autowired private BillItemService billItemService;
    @Autowired private BillPaymentService billPaymentService;
    @Autowired private BillRepository billRepository;
    @Autowired private BillItemRepository billItemRepository;
    @Autowired private BillPaymentRepository billPaymentRepository;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;
    @Autowired private TerminalController terminalController;
    @Autowired private MasterSyncController masterSyncController;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String terminalId;
    private String terminalSeries;

    @BeforeEach
    void setUp() {
        cleanupTenantData();
        seedRestaurantProfile();

        TenantContext.setCurrentTenant(RESTAURANT_ID);
        TenantContext.setCurrentRole("OWNER");
        TenantContext.setCurrentUserId(USER_ID);

        TerminalController.TerminalActivationResponse activation = activateTerminal();
        terminalId = activation.terminalId();
        terminalSeries = activation.terminalSeries();
        TenantContext.setCurrentTerminalId(terminalId);
        TenantContext.setCurrentTerminalSeries(terminalSeries);
        TenantContext.setCurrentTerminalDevice(DEVICE_ID);
        TenantContext.setCurrentTerminalActive(true);
    }

    @AfterEach
    void tearDown() {
        cleanupTenantData();
        TenantContext.clear();
    }

    @Test
    void pushBillItemsAndPayments_thenPullCompleteGraph() {
        long serverBillId = pushCompleteBillFixture();

        MasterSyncResponseDTO response = performMasterPull(0L);

        assertCompleteBillGraph(response, serverBillId);
    }

    @Test
    void repeatingIdenticalPush_doesNotCreateDuplicates() {
        long serverBillId = pushCompleteBillFixture();
        long billCountBefore = billRepository.countByRestaurantIdAndIsDeletedFalse(RESTAURANT_ID);
        int itemCountBefore = billItemRepository
                .findByServerBillIdAndIsDeletedFalseOrderById(serverBillId).size();
        int paymentCountBefore = billPaymentRepository
                .findByRestaurantIdAndServerBillIdIn(RESTAURANT_ID, List.of(serverBillId)).size();

        long repeatedServerBillId = pushCompleteBillFixture();

        assertThat(repeatedServerBillId).isEqualTo(serverBillId);
        assertThat(billRepository.countByRestaurantIdAndIsDeletedFalse(RESTAURANT_ID))
                .isEqualTo(billCountBefore);
        assertThat(billItemRepository.findByServerBillIdAndIsDeletedFalseOrderById(serverBillId))
                .hasSize(itemCountBefore);
        assertThat(billPaymentRepository
                .findByRestaurantIdAndServerBillIdIn(RESTAURANT_ID, List.of(serverBillId)))
                .hasSize(paymentCountBefore);
    }

    @Test
    void repeatingPaymentOperation_doesNotDuplicateFinancialEffect() {
        long serverBillId = pushCompleteBillFixture();
        BigDecimal totalBefore = totalPaymentAmount(serverBillId);
        List<BillPayment> repeatedPayments = createTestPayments(serverBillId);

        PushSyncResponse response = billPaymentService.pushData(RESTAURANT_ID, repeatedPayments);

        assertThat(response.getFailedLocalIds()).isEmpty();
        assertThat(response.getSuccessfulLocalIds()).contains(PAYMENT_LOCAL_ID);
        assertThat(totalPaymentAmount(serverBillId)).isEqualByComparingTo(totalBefore);
        assertThat(billPaymentRepository
                .findByRestaurantIdAndServerBillIdIn(RESTAURANT_ID, List.of(serverBillId)))
                .singleElement()
                .satisfies(payment -> {
                    assertThat(payment.getOperationId()).isEqualTo(paymentOperationId());
                    assertThat(payment.getAmount()).isEqualByComparingTo("250.00");
                });
    }

    private long pushCompleteBillFixture() {
        PushSyncResponse billResponse = billService.pushData(RESTAURANT_ID, List.of(createTestBill()));
        assertThat(billResponse.getFailedLocalIds()).isEmpty();
        assertThat(billResponse.getSuccessfulLocalIds()).contains(BILL_LOCAL_ID);

        Bill savedBill = billRepository
                .findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT_ID, DEVICE_ID, BILL_LOCAL_ID)
                .orElseThrow();
        long serverBillId = savedBill.getId();

        PushSyncResponse itemResponse = billItemService.pushData(
                RESTAURANT_ID, createTestBillItems(serverBillId));
        PushSyncResponse paymentResponse = billPaymentService.pushData(
                RESTAURANT_ID, createTestPayments(serverBillId));

        assertThat(itemResponse.getFailedLocalIds()).isEmpty();
        assertThat(itemResponse.getSuccessfulLocalIds())
                .containsExactlyInAnyOrder(ITEM_1_LOCAL_ID, ITEM_2_LOCAL_ID);
        assertThat(paymentResponse.getFailedLocalIds()).isEmpty();
        assertThat(paymentResponse.getSuccessfulLocalIds()).contains(PAYMENT_LOCAL_ID);
        return serverBillId;
    }

    private MasterSyncResponseDTO performMasterPull(long checkpoint) {
        return masterSyncController.pullMasterSync(
                checkpoint,
                DEVICE_ID,
                null,
                terminalId,
                true,
                0,
                500,
                new MockHttpServletRequest())
                .getBody();
    }

    private Bill createTestBill() {
        Bill bill = new Bill();
        applySyncIdentity(bill, BILL_LOCAL_ID);
        bill.setDailyOrderId(1L);
        bill.setDailyOrderDisplay(terminalSeries + "-01");
        bill.setTerminalSeries(terminalSeries);
        bill.setCreatedTerminalId(terminalId);
        bill.setCreatedDeviceId(DEVICE_ID);
        bill.setCurrentOwnerTerminalId(terminalId);
        bill.setFinancialYear("26");
        bill.setInvoiceSeries("26" + terminalSeries);
        bill.setInvoiceSequence(1L);
        bill.setInvoiceNumber("26" + terminalSeries + "-000001");
        bill.setOrderType("dine_in");
        bill.setSourceChannel("pos");
        bill.setSubtotal(new BigDecimal("230.00"));
        bill.setGstPercentage(new BigDecimal("8.70"));
        bill.setCgstAmount(new BigDecimal("10.00"));
        bill.setSgstAmount(new BigDecimal("10.00"));
        bill.setTotalAmount(new BigDecimal("250.00"));
        bill.setPaymentMode("cash");
        bill.setPaymentStatus("paid");
        bill.setOrderStatus("completed");
        bill.setCreatedBy(USER_ID);
        bill.setPaidAt(FIXED_TIMESTAMP);
        bill.setLastResetDate("2026-07-28");
        bill.setPublicToken(FIXED_PUBLIC_TOKEN);
        return bill;
    }

    private List<BillItem> createTestBillItems(long serverBillId) {
        return List.of(
                createBillItem(ITEM_1_LOCAL_ID, serverBillId, 501L,
                        "Butter Chicken", "150.00"),
                createBillItem(ITEM_2_LOCAL_ID, serverBillId, 502L,
                        "Jeera Rice", "80.00"));
    }

    private BillItem createBillItem(
            long localId,
            long serverBillId,
            long menuItemId,
            String itemName,
            String price) {
        BillItem item = new BillItem();
        applySyncIdentity(item, localId);
        item.setBillId(BILL_LOCAL_ID);
        item.setServerBillId(serverBillId);
        item.setMenuItemId(menuItemId);
        item.setItemName(itemName);
        item.setPrice(new BigDecimal(price));
        item.setQuantity(1);
        item.setItemTotal(new BigDecimal(price));
        return item;
    }

    private List<BillPayment> createTestPayments(long serverBillId) {
        BillPayment payment = new BillPayment();
        applySyncIdentity(payment, PAYMENT_LOCAL_ID);
        payment.setBillId(BILL_LOCAL_ID);
        payment.setServerBillId(serverBillId);
        payment.setPaymentMode("cash");
        payment.setAmount(new BigDecimal("250.00"));
        payment.setVerifiedBy("manual");
        payment.setOperationId(paymentOperationId());
        return List.of(payment);
    }

    private void applySyncIdentity(com.khanabook.saas.sync.entity.BaseSyncEntity entity, long localId) {
        entity.setLocalId(localId);
        entity.setDeviceId(DEVICE_ID);
        entity.setTerminalId(terminalId);
        entity.setRestaurantId(RESTAURANT_ID);
        entity.setCreatedAt(FIXED_TIMESTAMP);
        entity.setUpdatedAt(FIXED_TIMESTAMP);
        entity.setServerUpdatedAt(0L);
        entity.setIsDeleted(false);
    }

    private BigDecimal totalPaymentAmount(long serverBillId) {
        return billPaymentRepository
                .findByRestaurantIdAndServerBillIdIn(RESTAURANT_ID, List.of(serverBillId))
                .stream()
                .map(BillPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void assertCompleteBillGraph(MasterSyncResponseDTO response, long expectedServerBillId) {
        assertThat(response).isNotNull();
        assertThat(response.getBills())
                .anySatisfy(bill -> {
                    assertThat(bill.getLocalId()).isEqualTo(BILL_LOCAL_ID);
                    assertThat(bill.getId()).isEqualTo(expectedServerBillId);
                    assertThat(bill.getPublicToken()).isEqualTo(FIXED_PUBLIC_TOKEN);
                });
        assertThat(response.getBillItems())
                .filteredOn(item -> Objects.equals(expectedServerBillId, item.getServerBillId()))
                .extracting(item -> item.getLocalId())
                .containsExactlyInAnyOrder(ITEM_1_LOCAL_ID, ITEM_2_LOCAL_ID);
        assertThat(response.getBillPayments())
                .filteredOn(payment -> Objects.equals(expectedServerBillId, payment.getServerBillId()))
                .singleElement()
                .satisfies(payment -> {
                    assertThat(payment.getLocalId()).isEqualTo(PAYMENT_LOCAL_ID);
                    assertThat(payment.getOperationId()).isEqualTo(paymentOperationId());
                    assertThat(payment.getAmount()).isEqualByComparingTo("250.00");
                });
    }

    private String paymentOperationId() {
        return "OP-" + BILL_LOCAL_ID;
    }

    private TerminalController.TerminalActivationResponse activateTerminal() {
        var response = terminalController.activate(
                new TerminalController.TerminalActivationRequest(DEVICE_ID, null));
        assertThat(response.getStatusCode().value()).isIn(200, 201);
        assertThat(response.getBody()).isInstanceOf(TerminalController.TerminalActivationResponse.class);
        return (TerminalController.TerminalActivationResponse) response.getBody();
    }

    private void seedRestaurantProfile() {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(RESTAURANT_ID);
        profile.setLocalId(1L);
        profile.setDeviceId(DEVICE_ID);
        profile.setShopName("Bill Lifecycle Test Shop");
        profile.setCreatedAt(FIXED_TIMESTAMP);
        profile.setUpdatedAt(FIXED_TIMESTAMP);
        profile.setServerUpdatedAt(FIXED_TIMESTAMP);
        restaurantProfileRepository.save(profile);
    }

    private void cleanupTenantData() {
        jdbcTemplate.update("DELETE FROM bill_payments WHERE restaurant_id = ?", RESTAURANT_ID);
        jdbcTemplate.update("DELETE FROM bill_items WHERE restaurant_id = ?", RESTAURANT_ID);
        jdbcTemplate.update("DELETE FROM bills WHERE restaurant_id = ?", RESTAURANT_ID);
        jdbcTemplate.update("DELETE FROM device_registration_request WHERE restaurant_id = ?", RESTAURANT_ID);
        jdbcTemplate.update("DELETE FROM restaurant_terminal WHERE restaurant_id = ?", RESTAURANT_ID);
        restaurantProfileRepository.findByRestaurantId(RESTAURANT_ID)
                .ifPresent(restaurantProfileRepository::delete);
    }
}
