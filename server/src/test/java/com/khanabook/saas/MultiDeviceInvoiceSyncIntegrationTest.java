package com.khanabook.saas;

import com.khanabook.saas.controller.TerminalController;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.BillService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Automates PLAN §10's multi-device release gate: two offline terminals must produce
 * unique orders and invoices, the V26 series-aware uniqueness index must actually
 * reject a real collision, and the server-side fallback allocator must number bills
 * that a device pushed without an invoice number.
 *
 * <p>Runs against real PostgreSQL + Flyway (not H2) because the whole point is to
 * exercise the V26 migration's guarded indexes and the {@code gen_random_uuid()}
 * default — behaviours H2 does not model. Mirrors the datasource wiring in
 * {@link PostgresMigrationSmokeTest}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MultiDeviceInvoiceSyncIntegrationTest {

    private static final long TENANT = 9100L;
    private static final String DEVICE_A = "device-alpha";
    private static final String DEVICE_B = "device-beta";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("kbook_multidevice_test")
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

        registry.add("JWT_SECRET", () -> "multidevice-test-secret-64-chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-google-client-id");
        registry.add("PAYMENT_CRYPTO_SECRET", () -> "multidevice-payment-secret-32-bytes-min-xxxx");
        registry.add("APP_BASE_URL", () -> "https://test.khanabook.app");
    }

    @Autowired private BillService billService;
    @Autowired private BillRepository billRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;
    @Autowired private TerminalController terminalController;
    @Autowired private com.khanabook.saas.service.TerminalManagementService terminalManagementService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Isolate each test: the container is shared across the class.
        jdbcTemplate.update("DELETE FROM bills WHERE restaurant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM device_registration_request WHERE restaurant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM restaurant_terminal WHERE restaurant_id = ?", TENANT);
        restaurantProfileRepository.findByRestaurantId(TENANT)
                .ifPresent(restaurantProfileRepository::delete);
        seedRestaurantProfile();
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentRole("OWNER");
        TenantContext.setCurrentUserId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- PLAN §10 core gate: two offline devices, unique numbers, zero quarantine ----

    @Test
    @DisplayName("Two offline terminals get distinct series and produce unique, non-colliding invoices")
    void twoOfflineDevicesProduceUniqueInvoices() {
        // Step 2-3: each device activates a terminal online → distinct permanent series.
        String seriesA = activateTerminal(DEVICE_A);
        String seriesB = activateTerminal(DEVICE_B);
        assertThat(seriesA).isEqualTo("A1");
        assertThat(seriesB).isEqualTo("A2");

        // Step 4-6: both devices bill OFFLINE, each self-allocating within its own series.
        List<Bill> billsA = List.of(
                deviceBill(DEVICE_A, 1L, seriesA, 1L),
                deviceBill(DEVICE_A, 2L, seriesA, 2L),
                deviceBill(DEVICE_A, 3L, seriesA, 3L));
        List<Bill> billsB = List.of(
                deviceBill(DEVICE_B, 1L, seriesB, 1L),
                deviceBill(DEVICE_B, 2L, seriesB, 2L),
                deviceBill(DEVICE_B, 3L, seriesB, 3L));

        // Step 7: A comes online and syncs, then B.
        PushSyncResponse pushA = billService.pushData(TENANT, copyOf(billsA));
        PushSyncResponse pushB = billService.pushData(TENANT, copyOf(billsB));

        // Step 8: no quarantine — nothing failed on either push.
        assertThat(pushA.getFailedLocalIds()).isEmpty();
        assertThat(pushB.getFailedLocalIds()).isEmpty();

        List<Bill> persisted = billRepository.findByRestaurantIdAndIsDeletedFalse(TENANT);
        assertThat(persisted).hasSize(6);

        List<String> invoiceNumbers = persisted.stream()
                .map(Bill::getInvoiceNumber)
                .collect(Collectors.toList());
        assertThat(invoiceNumbers)
                .containsExactlyInAnyOrder(
                        "26A1-000001", "26A1-000002", "26A1-000003",
                        "26A2-000001", "26A2-000002", "26A2-000003")
                .doesNotHaveDuplicates();
    }

    // ---- Step 9: cross-device pull ----

    @Test
    @DisplayName("Device B pulls the bills Device A created")
    void deviceBPullsDeviceABills() {
        String seriesA = activateTerminal(DEVICE_A);
        billService.pushData(TENANT, copyOf(List.of(
                deviceBill(DEVICE_A, 1L, seriesA, 1L),
                deviceBill(DEVICE_A, 2L, seriesA, 2L))));

        // Recovery/cross-device pull (ignoreDeviceId=true) as Device B.
        List<Bill> pulled = billService.pullData(TENANT, 0L, DEVICE_B, true);

        List<Bill> fromA = pulled.stream()
                .filter(b -> DEVICE_A.equals(b.getDeviceId()))
                .collect(Collectors.toList());
        assertThat(fromA).hasSize(2);
        assertThat(fromA).allSatisfy(b -> assertThat(b.getInvoiceNumber()).startsWith("26A1-"));
    }

    // ---- Server-side fallback allocation ----

    @Test
    @DisplayName("Server allocates an invoice number when the device pushes one without it")
    void serverAllocatesInvoiceNumberWhenDeviceOmitsIt() {
        String seriesA = activateTerminal(DEVICE_A);

        Bill unnumbered = deviceBill(DEVICE_A, 1L, seriesA, 1L);
        unnumbered.setFinancialYear(null);
        unnumbered.setInvoiceSeries(null);
        unnumbered.setInvoiceSequence(null);
        unnumbered.setInvoiceNumber(null);

        billService.pushData(TENANT, List.of(unnumbered));

        Bill stored = billRepository.findByRestaurantIdAndIsDeletedFalse(TENANT).get(0);
        assertThat(stored.getInvoiceNumber()).matches("26A1-\\d{6}");
        assertThat(stored.getInvoiceSequence()).isEqualTo(1L);
        assertThat(stored.getInvoiceSeries()).isEqualTo("26A1");
    }

    // ---- Adversarial: the V26 unique index must reject a real collision ----

    @Test
    @DisplayName("A duplicate (series, financial year, sequence) is rejected by the V26 unique index")
    void duplicateInvoiceSequenceIsRejected() {
        // Sanity: the guarded index actually got created on this database.
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'ux_bills_restaurant_invoice_series_active'",
                Integer.class);
        assertThat(indexCount).isEqualTo(1);

        String seriesA = activateTerminal(DEVICE_A);
        billService.pushData(TENANT, List.of(deviceBill(DEVICE_A, 1L, seriesA, 1L))); // 26A1-000001

        // A different device/local row forced to claim the SAME invoice identity, with a
        // distinct daily order id so only the invoice-series index can reject it.
        Bill collider = deviceBill(DEVICE_B, 1L, seriesA, 1L); // same 26A1-000001
        collider.setDailyOrderId(999L);

        assertThatThrownBy(() -> billService.pushData(TENANT, List.of(collider)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(billRepository.findByRestaurantIdAndIsDeletedFalse(TENANT)).hasSize(1);
    }

    // ---- helpers ----

    /**
     * Activates a terminal following the production lifecycle:
     * - First device for OWNER: auto-creates Terminal A (201).
     * - Additional devices: submits a PENDING request (202), then approves it.
     */
    private String activateTerminal(String deviceId) {
        var response = terminalController
                .activate(new TerminalController.TerminalActivationRequest(deviceId, null));

        if (response.getStatusCode().value() == 200 || response.getStatusCode().value() == 201) {
            // Already activated or first-terminal auto-create
            return ((TerminalController.TerminalActivationResponse) response.getBody())
                    .terminalSeries();
        }

        if (response.getStatusCode().value() == 202) {
            // Pending approval — approve it via the management service
            var pending = (TerminalController.TerminalPendingResponse) response.getBody();
            var result = terminalManagementService.approveRequest(
                    pending.requestId(), TENANT, 1L, "OWNER");
            return result.terminal().getTerminalSeries();
        }

        throw new IllegalStateException("Unexpected activation response: " + response.getStatusCode());
    }

    /**
     * Builds a settled bill as a device would push it. When {@code series} is set, the
     * bill is pre-numbered {@code <fy><series>-<seq padded>} to mimic on-device
     * self-allocation; financial year is fixed to "26" for deterministic assertions.
     */
    private Bill deviceBill(String deviceId, long localId, String series, long sequence) {
        long now = System.currentTimeMillis();
        Bill bill = new Bill();
        bill.setRestaurantId(TENANT);
        bill.setDeviceId(deviceId);
        bill.setLocalId(localId);
        bill.setCreatedAt(now);
        bill.setUpdatedAt(now);
        bill.setDailyOrderId(sequence);
        bill.setLastResetDate("2026-07-12");
        bill.setOrderType("dine_in");
        bill.setSubtotal(new BigDecimal("100.00"));
        bill.setTotalAmount(new BigDecimal("100.00"));
        bill.setPaymentMode("cash");
        bill.setPaymentStatus("paid");
        bill.setOrderStatus("completed");

        String financialYear = "26";
        String invoiceSeries = financialYear + series;
        bill.setTerminalSeries(series);
        bill.setFinancialYear(financialYear);
        bill.setInvoiceSeries(invoiceSeries);
        bill.setInvoiceSequence(sequence);
        bill.setInvoiceNumber(invoiceSeries + "-" + String.format("%06d", sequence));
        return bill;
    }

    /** Fresh entity copies so a rejected push cannot leave managed state behind. */
    private List<Bill> copyOf(List<Bill> bills) {
        return bills.stream().map(b -> {
            Bill c = deviceBill(b.getDeviceId(), b.getLocalId(), b.getTerminalSeries(), b.getInvoiceSequence());
            return c;
        }).collect(Collectors.toList());
    }

    private void seedRestaurantProfile() {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(TENANT);
        profile.setLocalId(1L);
        profile.setDeviceId(DEVICE_A);
        profile.setShopName("Multi-Device Test Shop");
        profile.setCreatedAt(System.currentTimeMillis());
        profile.setUpdatedAt(System.currentTimeMillis());
        profile.setServerUpdatedAt(System.currentTimeMillis());
        restaurantProfileRepository.save(profile);
    }
}
