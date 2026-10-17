package com.khanabook.saas.feature.billing.service;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.sync.service.GenericSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillServiceImpl} focusing on the highest-risk logic:
 * invoice number allocation, Indian financial year calculation, sequence
 * increment within a batch, and trusted terminal context overwriting client
 * fields on push.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillServiceImplInvoiceAllocationTest {

    private static final long TENANT_ID = 9_900_001L;
    private static final String SERIES = "F";

    @Mock private BillRepository billRepository;
    @Mock private GenericSyncService genericSyncService;
    @Mock private RestaurantProfileRepository restaurantProfileRepository;
    @Mock private RestaurantTerminalRepository terminalRepository;

    private BillServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BillServiceImpl(billRepository, genericSyncService,
                restaurantProfileRepository, terminalRepository);

        RestaurantProfile profile = new RestaurantProfile();
        profile.setTimezone("Asia/Kolkata");
        when(restaurantProfileRepository.findByRestaurantId(TENANT_ID))
                .thenReturn(Optional.of(profile));

        when(terminalRepository.findAndLockByRestaurantIdAndTerminalSeries(
                eq(TENANT_ID), anyString()))
                .thenReturn(Optional.of(mock(RestaurantTerminal.class)));

        when(billRepository.findMaxInvoiceSequence(anyLong(), anyString(), anyString()))
                .thenReturn(0L);

        when(genericSyncService.handlePushSync(anyLong(), any(), any()))
                .thenReturn(new PushSyncResponse());

        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentTerminalId("term-1");
        TenantContext.setCurrentTerminalSeries(SERIES);
        TenantContext.setCurrentTerminalDevice("device-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Bill newBill(String terminalSeries, long createdAtEpochMilli) {
        Bill bill = new Bill();
        bill.setTerminalSeries(terminalSeries);
        bill.setCreatedAt(createdAtEpochMilli);
        bill.setUpdatedAt(createdAtEpochMilli);
        return bill;
    }

    private long epochMillis(LocalDate date) {
        return ZonedDateTime.of(date.atStartOfDay(), ZoneId.of("Asia/Kolkata"))
                .toInstant().toEpochMilli();
    }

    @Test
    void allocatesInvoiceNumber_whenMissing() {
        Bill bill = newBill(SERIES, epochMillis(LocalDate.of(2026, 4, 15)));

        service.pushData(TENANT_ID, List.of(bill));

        assertThat(bill.getFinancialYear()).isEqualTo("26");
        assertThat(bill.getInvoiceSeries()).isEqualTo("26F");
        assertThat(bill.getInvoiceSequence()).isEqualTo(1L);
        assertThat(bill.getInvoiceNumber()).isEqualTo("F000001");
    }

    @Test
    void incrementsSequence_fromExistingMax() {
        when(billRepository.findMaxInvoiceSequence(TENANT_ID, SERIES, "26"))
                .thenReturn(5L);
        Bill bill = newBill(SERIES, epochMillis(LocalDate.of(2026, 4, 15)));

        service.pushData(TENANT_ID, List.of(bill));

        assertThat(bill.getInvoiceSequence()).isEqualTo(6L);
        assertThat(bill.getInvoiceNumber()).isEqualTo("F000006");
    }

    @Test
    void financialYear_usesPreviousYear_forMarch() {
        // Indian FY runs Apr-Mar. A bill dated 31 Mar 2026 belongs to FY 25
        // (i.e. FY 2025-26 is encoded as "25").
        Bill bill = newBill(SERIES, epochMillis(LocalDate.of(2026, 3, 31)));

        service.pushData(TENANT_ID, List.of(bill));

        assertThat(bill.getFinancialYear()).isEqualTo("25");
        assertThat(bill.getInvoiceSeries()).isEqualTo("25F");
    }

    @Test
    void financialYear_usesCurrentYear_forApril() {
        Bill bill = newBill(SERIES, epochMillis(LocalDate.of(2026, 4, 1)));

        service.pushData(TENANT_ID, List.of(bill));

        assertThat(bill.getFinancialYear()).isEqualTo("26");
    }

    @Test
    void skipsBill_withExistingInvoiceNumber() {
        Bill bill = newBill(SERIES, epochMillis(LocalDate.of(2026, 4, 15)));
        bill.setInvoiceNumber("PRE-EXIST-001");
        bill.setInvoiceSeries("OLD");
        bill.setInvoiceSequence(99L);
        bill.setFinancialYear("25");

        service.pushData(TENANT_ID, List.of(bill));

        assertThat(bill.getInvoiceNumber()).isEqualTo("PRE-EXIST-001");
        assertThat(bill.getInvoiceSeries()).isEqualTo("OLD");
        assertThat(bill.getInvoiceSequence()).isEqualTo(99L);
        assertThat(bill.getFinancialYear()).isEqualTo("25");
    }

    @Test
    void skipsBill_withBlankTerminalSeries() {
        TenantContext.setCurrentTerminalSeries(null);
        Bill bill = newBill("", epochMillis(LocalDate.of(2026, 4, 15)));

        service.pushData(TENANT_ID, List.of(bill));

        assertThat(bill.getInvoiceNumber()).isNullOrEmpty();
        assertThat(bill.getInvoiceSeries()).isNullOrEmpty();
    }

    @Test
    void truncatesInvoiceNumber_whenExceedsMaxLength() {
        // 15-char series letter + long sequence would exceed GST 16-char cap
        String longSeries = "ABCDEFGHIJKLMNO";
        when(terminalRepository.findAndLockByRestaurantIdAndTerminalSeries(
                eq(TENANT_ID), eq(longSeries)))
                .thenReturn(Optional.of(mock(RestaurantTerminal.class)));
        when(billRepository.findMaxInvoiceSequence(TENANT_ID, longSeries, "26"))
                .thenReturn(999_999L);

        Bill bill = newBill(longSeries, epochMillis(LocalDate.of(2026, 4, 15)));

        service.pushData(TENANT_ID, List.of(bill));

        assertThat(bill.getInvoiceNumber()).hasSizeLessThanOrEqualTo(16);
    }

    @Test
    void allocatesMultipleBills_incrementallyInSameBatch() {
        Bill a = newBill(SERIES, epochMillis(LocalDate.of(2026, 4, 15)));
        Bill b = newBill(SERIES, epochMillis(LocalDate.of(2026, 4, 15)));
        Bill c = newBill(SERIES, epochMillis(LocalDate.of(2026, 4, 15)));

        service.pushData(TENANT_ID, List.of(a, b, c));

        assertThat(a.getInvoiceSequence()).isEqualTo(1L);
        assertThat(b.getInvoiceSequence()).isEqualTo(2L);
        assertThat(c.getInvoiceSequence()).isEqualTo(3L);
        assertThat(a.getInvoiceNumber()).isEqualTo("F000001");
        assertThat(c.getInvoiceNumber()).isEqualTo("F000003");
    }

    @Test
    void trustedTerminalContext_overwritesClientFields() {
        Bill bill = newBill(SERIES, epochMillis(LocalDate.of(2026, 4, 15)));
        bill.setTerminalId("rogue-terminal");
        bill.setDeviceId("rogue-device");

        service.pushData(TENANT_ID, List.of(bill));

        assertThat(bill.getTerminalId()).isEqualTo("term-1");
        assertThat(bill.getDeviceId()).isEqualTo("device-1");
        assertThat(bill.getCreatedDeviceId()).isEqualTo("device-1");
    }

    @Test
    void fallbackToCreatedAt_whenBothTimestampsNull() {
        Bill bill = newBill(SERIES, System.currentTimeMillis());
        bill.setCreatedAt(null);
        bill.setUpdatedAt(null);

        service.pushData(TENANT_ID, List.of(bill));

        // Should not throw; last reset date is derived from current time
        assertThat(bill.getLastResetDate()).isNotNull();
    }
}
