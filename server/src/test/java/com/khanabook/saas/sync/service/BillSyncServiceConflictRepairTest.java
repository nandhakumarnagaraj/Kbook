package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * A duplicate daily order number must be repaired, not rejected.
 *
 * <p>Rejecting it created an unrecoverable loop: the client re-pushed the same bill, the
 * server re-rejected it, and the client's local auto-heal could renumber to the same value
 * because it can only see rows in the local database. The daily order number is an
 * operational display value, so reassigning it is safe — unlike the invoice number, which
 * is the tax-legal identifier and must still be rejected.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillSyncServiceConflictRepairTest {

    private static final long TENANT = 4912877311365041681L;

    @Mock private BillRepository billRepository;
    @Mock private RestaurantProfileRepository restaurantProfileRepository;

    private BillSyncService service() {
        return new BillSyncService(billRepository, restaurantProfileRepository);
    }

    private Bill incoming(long localId, long dailyOrderId, String display) {
        Bill bill = new Bill();
        bill.setLocalId(localId);
        bill.setDeviceId("dev_173a4eb4");
        bill.setPublicToken(UUID.randomUUID());
        bill.setTerminalSeries("F");
        bill.setLastResetDate("2026-09-12");
        bill.setDailyOrderId(dailyOrderId);
        bill.setDailyOrderDisplay(display);
        bill.setIsDeleted(false);
        return bill;
    }

    private void conflictExists(long conflictingId) {
        Bill existing = new Bill();
        existing.setId(conflictingId);
        existing.setTerminalSeries("F");
        when(billRepository.findConflictingDailyOrder(
                anyLong(), anyString(), anyLong(), anyString(), anyLong(), any(), any()))
                .thenReturn(Optional.of(existing));
    }

    private void noConflict() {
        when(billRepository.findConflictingDailyOrder(
                anyLong(), anyString(), anyLong(), anyString(), anyLong(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void conflictingBillIsReassignedTheNextFreeNumber() {
        // This is the live incident: F-01 for 2026-09-12 is already held by another bill.
        conflictExists(123L);
        when(billRepository.findMaxDailyOrderId(eq(TENANT), eq("2026-09-12"), eq("F"))).thenReturn(5L);
        Bill bill = incoming(112L, 1L, "F-01");

        service().validateBillNumberConflicts(TENANT, bill, billRepository);

        assertThat(bill.getDailyOrderId()).isEqualTo(6L);
        assertThat(bill.getDailyOrderDisplay()).isEqualTo("F-06");
    }

    @Test
    void repairDoesNotThrow_soThePushIsNoLongerRetriedForever() {
        conflictExists(123L);
        when(billRepository.findMaxDailyOrderId(anyLong(), anyString(), any())).thenReturn(1L);
        Bill bill = incoming(112L, 1L, "F-01");

        // Previously this threw IllegalStateException -> failedLocalIds -> client retry loop.
        service().validateBillNumberConflicts(TENANT, bill, billRepository);

        assertThat(bill.getDailyOrderId()).isEqualTo(2L);
    }

    @Test
    void repairedNumberIsAlwaysAboveEveryExistingNumberForThatDayAndSeries() {
        conflictExists(123L);
        when(billRepository.findMaxDailyOrderId(anyLong(), anyString(), any())).thenReturn(26L);
        Bill bill = incoming(112L, 1L, "F-01");

        service().validateBillNumberConflicts(TENANT, bill, billRepository);

        // 27, not 2 — so it cannot land on another occupied slot and conflict again.
        assertThat(bill.getDailyOrderId()).isEqualTo(27L);
        assertThat(bill.getDailyOrderDisplay()).isEqualTo("F-27");
    }

    @Test
    void anEmptyDayStartsAtOne() {
        conflictExists(123L);
        when(billRepository.findMaxDailyOrderId(anyLong(), anyString(), any())).thenReturn(null);
        Bill bill = incoming(112L, 1L, "F-01");

        service().validateBillNumberConflicts(TENANT, bill, billRepository);

        assertThat(bill.getDailyOrderId()).isEqualTo(1L);
    }

    @Test
    void aBillWithNoConflictIsLeftAlone() {
        noConflict();
        Bill bill = incoming(112L, 7L, "F-07");

        service().validateBillNumberConflicts(TENANT, bill, billRepository);

        assertThat(bill.getDailyOrderId()).isEqualTo(7L);
        assertThat(bill.getDailyOrderDisplay()).isEqualTo("F-07");
    }

    @Test
    void duplicateInvoiceNumberIsStillRejected() {
        // The invoice number is the tax-legal identifier; it must never be silently rewritten.
        noConflict();
        Bill bill = incoming(112L, 7L, "F-07");
        bill.setFinancialYear("2026-27");
        bill.setInvoiceSeries("F");
        bill.setInvoiceSequence(26L);
        bill.setInvoiceNumber("F000026");
        Bill existing = new Bill();
        existing.setId(123L);
        existing.setTerminalSeries("F");
        when(billRepository.findConflictingInvoiceSeries(
                anyLong(), anyString(), anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().validateBillNumberConflicts(TENANT, bill, billRepository))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate invoice");
    }

    @Test
    void twoConflictingBillsInOneBatchGetDifferentNumbers() {
        // findMaxDailyOrderId only sees committed rows, so both bills read the same max.
        // Without per-batch reservation they'd both be assigned 6 and then collide with each
        // other at saveAll time — tripping the very unique index this check exists to protect.
        conflictExists(123L);
        when(billRepository.findMaxDailyOrderId(anyLong(), anyString(), any())).thenReturn(5L);
        java.util.Map<String, Long> batch = new java.util.HashMap<>();
        Bill first = incoming(112L, 1L, "F-01");
        Bill second = incoming(113L, 1L, "F-01");

        BillSyncService service = service();
        service.validateBillNumberConflicts(TENANT, first, billRepository, batch);
        service.validateBillNumberConflicts(TENANT, second, billRepository, batch);

        assertThat(first.getDailyOrderId()).isEqualTo(6L);
        assertThat(second.getDailyOrderId()).isEqualTo(7L);
        assertThat(second.getDailyOrderDisplay()).isEqualTo("F-07");
    }

    @Test
    void batchReservationIsScopedPerDayAndSeries() {
        conflictExists(123L);
        when(billRepository.findMaxDailyOrderId(anyLong(), anyString(), any())).thenReturn(5L);
        java.util.Map<String, Long> batch = new java.util.HashMap<>();
        Bill fSeries = incoming(112L, 1L, "F-01");
        Bill gSeries = incoming(113L, 1L, "G-01");
        gSeries.setTerminalSeries("G");

        BillSyncService service = service();
        service.validateBillNumberConflicts(TENANT, fSeries, billRepository, batch);
        service.validateBillNumberConflicts(TENANT, gSeries, billRepository, batch);

        // Different series are independent number spaces, so G must not be pushed to 7.
        assertThat(fSeries.getDailyOrderId()).isEqualTo(6L);
        assertThat(gSeries.getDailyOrderId()).isEqualTo(6L);
    }

    @Test
    void displayHasNoSeriesPrefixWhenTheTerminalHasNoSeries() {
        assertThat(BillSyncService.buildDailyOrderDisplay(null, 4L)).isEqualTo("04");
        assertThat(BillSyncService.buildDailyOrderDisplay("", 4L)).isEqualTo("04");
        assertThat(BillSyncService.buildDailyOrderDisplay("F", 4L)).isEqualTo("F-04");
        assertThat(BillSyncService.buildDailyOrderDisplay("F", 112L)).isEqualTo("F-112");
    }
}
