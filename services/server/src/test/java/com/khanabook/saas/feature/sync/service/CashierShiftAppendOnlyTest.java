package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.feature.shifts.data.CashierShift;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A sealed shift is append-only under last-write-wins, and the money the server computed
 * is not the terminal's to assert.
 *
 * <p>These call {@code preserveServerOwnedState} directly rather than going through a
 * push: it is the single place the guard lives, and a full push needs a database.
 */
class CashierShiftAppendOnlyTest {

    private static CashierShift sealedShift() {
        CashierShift shift = new CashierShift();
        shift.setStatus(CashierShift.STATUS_CLOSED);
        shift.setClosedAt(1_700_000_000_000L);
        shift.setDeclaredCash(new BigDecimal("1250.00"));
        shift.setExpectedCash(new BigDecimal("1240.00"));
        shift.setCashVariance(new BigDecimal("10.00"));
        shift.setTotalRevenue(new BigDecimal("9800.00"));
        shift.setTotalOrders(42);
        shift.setCloseSource("CASHIER");
        shift.setSnapshotJson("{\"v\":1}");
        shift.setOpenedAt(1_699_990_000_000L);
        shift.setBusinessDate(LocalDate.of(2026, 11, 10));
        return shift;
    }

    private static CashierShift staleCopyOf(CashierShift original) {
        CashierShift incoming = new CashierShift();
        incoming.setStatus(CashierShift.STATUS_ACTIVE);
        incoming.setDeclaredCash(new BigDecimal("1.00"));
        incoming.setExpectedCash(new BigDecimal("9999.00"));
        incoming.setCashVariance(new BigDecimal("9998.00"));
        incoming.setTotalRevenue(new BigDecimal("9999.00"));
        incoming.setTotalOrders(1);
        incoming.setCloseSource("CUTOFF_CRON");
        incoming.setSnapshotJson("{}");
        incoming.setOpenedAt(1L);
        incoming.setBusinessDate(LocalDate.of(2026, 11, 11));
        return incoming;
    }

    @Test
    void aStalePushCannotReopenOrRewriteASealedShift() {
        CashierShift existing = sealedShift();
        CashierShift incoming = staleCopyOf(existing);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getStatus()).isEqualTo(CashierShift.STATUS_CLOSED);
        assertThat(incoming.getClosedAt()).isEqualTo(existing.getClosedAt());
        assertThat(incoming.getDeclaredCash()).isEqualByComparingTo("1250.00");
        assertThat(incoming.getCloseSource()).isEqualTo("CASHIER");
        assertThat(incoming.getSnapshotJson()).isEqualTo("{\"v\":1}");
        assertThat(incoming.getBusinessDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(incoming.getOpenedAt()).isEqualTo(existing.getOpenedAt());
    }

    @Test
    void theServerComputedMoneyIsNeverTakenFromTheTerminal() {
        CashierShift existing = sealedShift();
        CashierShift incoming = staleCopyOf(existing);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getExpectedCash()).isEqualByComparingTo("1240.00");
        assertThat(incoming.getCashVariance()).isEqualByComparingTo("10.00");
        assertThat(incoming.getTotalRevenue()).isEqualByComparingTo("9800.00");
        assertThat(incoming.getTotalOrders()).isEqualTo(42);
    }

    @Test
    void anOpenShiftStillAcceptsTheCashiersDeclaredCount() {
        CashierShift existing = new CashierShift();
        existing.setStatus(CashierShift.STATUS_ACTIVE);
        existing.setExpectedCash(new BigDecimal("1240.00"));

        CashierShift incoming = new CashierShift();
        incoming.setStatus(CashierShift.STATUS_CLOSED);
        incoming.setDeclaredCash(new BigDecimal("1250.00"));

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        // The seal transition is allowed through — declared cash is device truth.
        assertThat(incoming.getStatus()).isEqualTo(CashierShift.STATUS_CLOSED);
        assertThat(incoming.getDeclaredCash()).isEqualByComparingTo("1250.00");
        // ...but the expected figure is still the server's.
        assertThat(incoming.getExpectedCash()).isEqualByComparingTo("1240.00");
    }
}
