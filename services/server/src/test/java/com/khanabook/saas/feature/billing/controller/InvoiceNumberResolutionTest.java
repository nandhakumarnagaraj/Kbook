package com.khanabook.saas.feature.billing.controller;

import com.khanabook.saas.feature.billing.data.Bill;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the invoice-number fallback chain used by the public invoice page.
 *
 * Regression target: bills created under the V26 multi-device identity scheme
 * have a NULL lifetime_order_id, and the old template rendered "INVnull".
 * The resolver must mirror Android's BillEntity.getInvoiceNumberDisplay().
 */
class InvoiceNumberResolutionTest {

    private final InvoiceController controller = new InvoiceController(null, null, null, null);

    private Bill bill(Long id) {
        Bill bill = new Bill();
        bill.setId(id);
        return bill;
    }

    @Test
    void structuredInvoiceNumberWins() {
        Bill bill = bill(371L);
        bill.setInvoiceNumber("E000030");
        bill.setLifetimeOrderId(9L);
        bill.setDailyOrderDisplay("E02");

        assertThat(controller.resolveInvoiceNumber(bill, "E02")).isEqualTo("E000030");
    }

    @Test
    void fallsBackToTerminalSeriesAndSequence() {
        Bill bill = bill(371L);
        bill.setTerminalSeries("e");
        bill.setInvoiceSequence(30L);

        assertThat(controller.resolveInvoiceNumber(bill, "")).isEqualTo("E30");
    }

    @Test
    void fallsBackToLegacyLifetimeOrderId() {
        Bill bill = bill(371L);
        bill.setLifetimeOrderId(42L);

        assertThat(controller.resolveInvoiceNumber(bill, "")).isEqualTo("INV42");
    }

    @Test
    void fallsBackToDailyOrderDisplay() {
        Bill bill = bill(371L);

        assertThat(controller.resolveInvoiceNumber(bill, "E02")).isEqualTo("E02");
    }

    @Test
    void nullLifetimeOrderIdNeverRendersInvnull() {
        Bill bill = bill(371L);
        bill.setInvoiceNumber("E000030");
        bill.setLifetimeOrderId(null);

        String resolved = controller.resolveInvoiceNumber(bill, "");
        assertThat(resolved).isEqualTo("E000030");
        assertThat(resolved).doesNotContainIgnoringCase("null");
    }

    @Test
    void lastResortFallbackIsDeterministicAndNonNull() {
        Bill bill = bill(371L);

        String resolved = controller.resolveInvoiceNumber(bill, "");
        assertThat(resolved).isEqualTo("INV-371");
        assertThat(resolved).doesNotContainIgnoringCase("null");
    }

    @Test
    void orderCodePrefersDailyOrderDisplay() {
        Bill bill = bill(371L);
        bill.setLifetimeOrderId(42L);

        assertThat(controller.resolveOrderCode(bill, "E02", "E000030")).isEqualTo("E02");
    }

    @Test
    void orderCodeFallsBackToLegacyLifetimeOrderId() {
        Bill bill = bill(371L);
        bill.setLifetimeOrderId(42L);

        assertThat(controller.resolveOrderCode(bill, "", "INV42")).isEqualTo("ORD42");
    }

    @Test
    void orderCodeNeverRendersOrdnull() {
        Bill bill = bill(371L);

        String orderCode = controller.resolveOrderCode(bill, "", "E000030");
        assertThat(orderCode).isEqualTo("E000030");
        assertThat(orderCode).doesNotContainIgnoringCase("null");
    }
}
