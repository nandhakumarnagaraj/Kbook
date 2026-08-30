package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.webadmin.service.BusinessReadService;
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
    void voidCompletedBill_throws() {
        Bill saved = billRepository.save(createTestBill(1L, "completed", "paid"));
        assertThrows(IllegalArgumentException.class,
                () -> businessReadService.voidBill(saved.getRestaurantId(), saved.getId(), "nope"));
    }

    @Test
    void voidPaidBill_throws() {
        Bill saved = billRepository.save(createTestBill(1L, "paid", "paid"));
        assertThrows(IllegalArgumentException.class,
                () -> businessReadService.voidBill(saved.getRestaurantId(), saved.getId(), "nope"));
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
