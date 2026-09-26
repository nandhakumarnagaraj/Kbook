package com.khanabook.saas.sync;

import com.khanabook.saas.feature.sync.controller.MasterSyncController;
import com.khanabook.saas.feature.restaurants.controller.TerminalController;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.billing.service.BillService;
import com.khanabook.saas.feature.sync.data.MasterSyncResponseDTO;
import com.khanabook.saas.core.security.TenantContext;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test verifying that {@code sourceChannel} survives the
 * Android->Server->Android sync round-trip unchanged.
 *
 * <p>Three cases:
 * <ul>
 *   <li>Explicit channel value round-trips unchanged.</li>
 *   <li>Empty-string default round-trips unchanged.</li>
 *   <li>Null (from a pre-field client) is normalized server-side to empty string.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SourceChannelSyncContractTest {

    private static final long RESTAURANT_ID = 9300L;
    private static final long USER_ID = 1L;
    private static final String DEVICE_ID = "source-channel-device";
    private static final long FIXED_TIMESTAMP =
            Instant.now().minus(java.time.Duration.ofHours(1)).toEpochMilli();
    private static final UUID FIXED_PUBLIC_TOKEN =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("kbook_source_channel_test")
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
                "source-channel-test-secret-64-chars-xxxxxxxxxxxxxxxxxxxxxxxxxx");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-google-client-id");
        registry.add("PAYMENT_CRYPTO_SECRET", () ->
                "source-channel-payment-secret-32-bytes-min-xxx");
        registry.add("APP_BASE_URL", () -> "https://test.khanabook.app");
    }

    @Autowired private BillService billService;
    @Autowired private BillRepository billRepository;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;
    @Autowired private TerminalController terminalController;
    @Autowired private MasterSyncController masterSyncController;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String terminalId;

    @BeforeEach
    void setUp() {
        cleanupTenantData();
        seedRestaurantProfile();

        TenantContext.setCurrentTenant(RESTAURANT_ID);
        TenantContext.setCurrentRole("OWNER");
        TenantContext.setCurrentUserId(USER_ID);

        TerminalController.TerminalActivationResponse activation = activateTerminal();
        terminalId = activation.terminalId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void sourceChannel_explicitValueRoundTrips() {
        long localId = 910001L;
        Bill pushed = buildBill(localId, "zomato");

        billService.pushData(RESTAURANT_ID, List.of(pushed));

        Bill stored = billRepository
                .findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT_ID, DEVICE_ID, localId)
                .orElseThrow();
        assertThat(stored.getSourceChannel()).isEqualTo("zomato");

        MasterSyncResponseDTO pulled = masterSyncController.pullMasterSync(
                0L, DEVICE_ID, null, terminalId, true, 0, 500, new MockHttpServletRequest()
        ).getBody();

        assertThat(pulled.getBills())
                .filteredOn(bill -> bill.getLocalId().equals(localId))
                .singleElement()
                .satisfies(bill -> assertThat(bill.getSourceChannel()).isEqualTo("zomato"));
    }

    @Test
    void sourceChannel_emptyStringRoundTrips() {
        long localId = 910002L;
        Bill pushed = buildBill(localId, "");

        billService.pushData(RESTAURANT_ID, List.of(pushed));

        Bill stored = billRepository
                .findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT_ID, DEVICE_ID, localId)
                .orElseThrow();
        assertThat(stored.getSourceChannel()).isEqualTo("");

        MasterSyncResponseDTO pulled = masterSyncController.pullMasterSync(
                0L, DEVICE_ID, null, terminalId, true, 0, 500, new MockHttpServletRequest()
        ).getBody();

        assertThat(pulled.getBills())
                .filteredOn(bill -> bill.getLocalId().equals(localId))
                .singleElement()
                .satisfies(bill -> assertThat(bill.getSourceChannel()).isEqualTo(""));
    }

    @Test
    void sourceChannel_nullIsNormalizedToEmpty() {
        long localId = 910003L;
        Bill pushed = buildBill(localId, null);

        billService.pushData(RESTAURANT_ID, List.of(pushed));

        Bill stored = billRepository
                .findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT_ID, DEVICE_ID, localId)
                .orElseThrow();
        assertThat(stored.getSourceChannel())
                .as("null from old client must not crash push; server normalizes to empty")
                .isNotNull()
                .isEqualTo("");

        MasterSyncResponseDTO pulled = masterSyncController.pullMasterSync(
                0L, DEVICE_ID, null, terminalId, true, 0, 500, new MockHttpServletRequest()
        ).getBody();

        assertThat(pulled.getBills())
                .filteredOn(bill -> bill.getLocalId().equals(localId))
                .singleElement()
                .satisfies(bill -> {
                    assertThat(bill.getSourceChannel()).isNotNull();
                    assertThat(bill.getSourceChannel()).isEqualTo("");
                });
    }

    private Bill buildBill(long localId, String sourceChannel) {
        Bill bill = new Bill();
        bill.setLocalId(localId);
        bill.setDeviceId(DEVICE_ID);
        bill.setTerminalId(terminalId);
        bill.setRestaurantId(RESTAURANT_ID);
        bill.setCreatedAt(FIXED_TIMESTAMP);
        bill.setUpdatedAt(FIXED_TIMESTAMP);
        bill.setServerUpdatedAt(0L);
        bill.setIsDeleted(false);

        bill.setDailyOrderId(localId);
        bill.setDailyOrderDisplay("#" + localId);
        bill.setOrderType("order");
        bill.setSourceChannel(sourceChannel);
        bill.setCustomerName("Walk-in");
        bill.setSubtotal(new BigDecimal("100.00"));
        bill.setGstPercentage(new BigDecimal("5.00"));
        bill.setCgstAmount(new BigDecimal("2.50"));
        bill.setSgstAmount(new BigDecimal("2.50"));
        bill.setCustomTaxAmount(BigDecimal.ZERO);
        bill.setTotalAmount(new BigDecimal("105.00"));
        bill.setPaymentMode("cash");
        bill.setPartAmount1(new BigDecimal("105.00"));
        bill.setPartAmount2(BigDecimal.ZERO);
        bill.setPaymentStatus("paid");
        bill.setOrderStatus("completed");
        bill.setStatusVersion(0);
        bill.setCreatedBy(USER_ID);
        bill.setPaidAt(FIXED_TIMESTAMP);
        bill.setLastResetDate("2026-09-26");
        bill.setCancelReason("");
        bill.setPublicToken(FIXED_PUBLIC_TOKEN);
        return bill;
    }

    private TerminalController.TerminalActivationResponse activateTerminal() {
        var response = terminalController.activate(
                new TerminalController.TerminalActivationRequest(DEVICE_ID, null, null));
        assertThat(response.getStatusCode().value()).isIn(200, 201);
        assertThat(response.getBody()).isInstanceOf(TerminalController.TerminalActivationResponse.class);
        return (TerminalController.TerminalActivationResponse) response.getBody();
    }

    private void seedRestaurantProfile() {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(RESTAURANT_ID);
        profile.setLocalId(1L);
        profile.setDeviceId(DEVICE_ID);
        profile.setShopName("Source Channel Test Shop");
        profile.setCreatedAt(FIXED_TIMESTAMP);
        profile.setUpdatedAt(FIXED_TIMESTAMP);
        profile.setServerUpdatedAt(FIXED_TIMESTAMP);
        profile.setLastResetDateProper(java.time.LocalDate.of(2026, 9, 26));
        profile.setLogoVersion(0);
        profile.setUpiQrVersion(0);
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
