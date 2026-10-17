package com.khanabook.saas.sync;

import com.khanabook.saas.feature.billing.service.BillSyncService;
import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.business.service.BusinessReadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BillVoidIntegrationTest extends BaseIntegrationTest {

    @Autowired private BillRepository billRepository;
    @Autowired private BusinessReadService businessReadService;

    @Test
    void voidDraftBill_succeeds() {
        Bill saved = billRepository.save(createTestBill(1L, "draft", "pending"));
        businessReadService.voidBill(saved.getRestaurantId(), saved.getId(), "Test void");

        Bill refreshed = billRepository.findById(saved.getId()).orElseThrow();
        assertEquals("cancelled", refreshed.getOrderStatus());
        assertEquals("cancelled", refreshed.getPaymentStatus());
        assertEquals("Test void", refreshed.getCancelReason());
    }

    @Test
    void voidCompletedBill_succeedsAndBumpsStatusVersion() {
        Bill saved = billRepository.save(createTestBill(1L, "completed", "paid"));
        int versionBefore = saved.getStatusVersion() == null ? 0 : saved.getStatusVersion();

        businessReadService.voidBill(saved.getRestaurantId(), saved.getId(), "Wrong order");

        Bill refreshed = billRepository.findById(saved.getId()).orElseThrow();
        assertEquals("cancelled", refreshed.getOrderStatus());
        assertEquals("cancelled", refreshed.getPaymentStatus());
        assertEquals("Wrong order", refreshed.getCancelReason());
        // The bump is what lets BillSyncService.protectBillState tell this deliberate
        // edit apart from a stale device push and stop restoring the finalized state.
        assertTrue(refreshed.getStatusVersion() > versionBefore,
                "voiding a finalized bill must bump statusVersion");
    }

    @Test
    void voidPaidBill_succeeds() {
        Bill saved = billRepository.save(createTestBill(1L, "paid", "paid"));
        businessReadService.voidBill(saved.getRestaurantId(), saved.getId(), "Refunded in cash");

        Bill refreshed = billRepository.findById(saved.getId()).orElseThrow();
        assertEquals("cancelled", refreshed.getOrderStatus());
        assertEquals("cancelled", refreshed.getPaymentStatus());
    }

    @Test
    void voidAlreadyCancelledBill_throws() {
        Bill saved = billRepository.save(createTestBill(1L, "cancelled", "cancelled"));
        assertThrows(IllegalArgumentException.class,
                () -> businessReadService.voidBill(saved.getRestaurantId(), saved.getId(), "nope"));
    }

    @Test
    void voidBill_wrongTenant_throws() {
        Bill saved = billRepository.save(createTestBill(1L, "draft", "pending"));
        assertThrows(IllegalArgumentException.class,
                () -> businessReadService.voidBill(88888L, saved.getId(), "wrong"));
    }

    private Bill createTestBill(Long tenantId, String orderStatus, String paymentStatus) {
        Bill bill = new Bill();
        long now = System.currentTimeMillis();
        bill.setLocalId(System.nanoTime());
        bill.setRestaurantId(tenantId);
        bill.setDeviceId("test-device");
        bill.setPublicToken(UUID.randomUUID());
        bill.setDailyOrderId(1L);
        bill.setDailyOrderDisplay("D-1");
        bill.setOrderType("order");
        bill.setSubtotal(new BigDecimal("100.00"));
        bill.setTotalAmount(new BigDecimal("100.00"));
        bill.setGstPercentage(new BigDecimal("0.0"));
        bill.setCgstAmount(new BigDecimal("0.0"));
        bill.setSgstAmount(new BigDecimal("0.0"));
        bill.setCustomTaxAmount(new BigDecimal("0.0"));
        bill.setPartAmount1(new BigDecimal("0.0"));
        bill.setPartAmount2(new BigDecimal("0.0"));
        bill.setPaymentMode("cash");
        bill.setOrderStatus(orderStatus);
        bill.setPaymentStatus(paymentStatus);
        bill.setLastResetDate("");
        bill.setIsDeleted(false);
        bill.setInventoryDeducted(false);
        bill.setCreatedAt(now);
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
        return bill;
    }
}
