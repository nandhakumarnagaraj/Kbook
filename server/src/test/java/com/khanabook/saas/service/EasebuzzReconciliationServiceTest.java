package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Transactional
class EasebuzzReconciliationServiceTest extends BaseIntegrationTest {

    @Autowired private EasebuzzReconciliationService reconciliationService;
    @Autowired private BillRepository billRepo;
    @Autowired private EasebuzzSubMerchantRepository subMerchantRepo;

    @MockBean private EasebuzzApiClient easebuzzApi;
    @MockBean private SubMerchantService subMerchantService;

    private Long testRestaurantId;
    private String testSubMerchantId;

    @BeforeEach
    void setUp() {
        testRestaurantId = 990001L;
        testSubMerchantId = "S360RECON" + System.currentTimeMillis();
    }

    @Test
    void testSplitReconciliationMatchesPayoutAmount() {
        Bill bill = createPaidBill("KB_RECON_MATCH", new BigDecimal("1000.00"), new BigDecimal("50.00"));

        mockSubMerchant(testSubMerchantId);
        mockSettlements(bill.getGatewayTxnId(), "950.00", "sm_" + testSubMerchantId);

        Map<String, Object> result = reconciliationService.reconcileSplits("2026-09-09");

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("splitMatched"));
        assertEquals(0, result.get("splitMismatches"));
        assertEquals(0, result.get("splitOrphanTransactions"));
        assertEquals(0, result.get("splitMissingSettlements"));
    }

    @Test
    void testSplitReconciliationFlagsAmountMismatch() {
        Bill bill = createPaidBill("KB_RECON_MISMATCH", new BigDecimal("1000.00"), new BigDecimal("50.00"));

        mockSubMerchant(testSubMerchantId);
        mockSettlements(bill.getGatewayTxnId(), "900.00", "sm_" + testSubMerchantId);

        Map<String, Object> result = reconciliationService.reconcileSplits("2026-09-09");

        assertEquals("success", result.get("status"));
        assertEquals(0, result.get("splitMatched"));
        assertEquals(1, result.get("splitMismatches"));
    }

    @Test
    void testSplitReconciliationOrphanRowWhenNoBill() {
        mockSubMerchant(testSubMerchantId);
        mockSettlements("KB_RECON_ORPHAN", "950.00", "sm_" + testSubMerchantId);

        Map<String, Object> result = reconciliationService.reconcileSplits("2026-09-09");

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("splitOrphanTransactions"));
        assertEquals(0, result.get("splitMatched"));
    }

    @Test
    void testSplitReconciliationParsesMsgNestedShape() {
        Bill bill = createPaidBill("KB_RECON_NESTED", new BigDecimal("2000.00"), new BigDecimal("100.00"));

        mockSubMerchant(testSubMerchantId);
        when(easebuzzApi.retrieveSettlements(any()))
            .thenReturn(Map.of(
                "status", true,
                "msg", List.of(Map.of(
                    "txnid", bill.getGatewayTxnId(),
                    "submerchant_id", testSubMerchantId,
                    "label", "sm_" + testSubMerchantId,
                    "payout_amount", "1900.00"
                ))
            ));

        Map<String, Object> result = reconciliationService.reconcileSplits("2026-09-09");

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("splitMatched"));
        assertEquals(0, result.get("splitMismatches"));
    }

    @Test
    void testSplitReconciliationFallsBackToAmountMinusCharges() {
        Bill bill = createPaidBill("KB_RECON_FALLBACK", new BigDecimal("1000.00"), new BigDecimal("50.00"));

        mockSubMerchant(testSubMerchantId);
        // No payout_amount: derive from amount − peb_service_charge − peb_service_tax
        when(easebuzzApi.retrieveSettlements(any()))
            .thenReturn(Map.of(
                "status", true,
                "data", List.of(Map.of(
                    "txnid", bill.getGatewayTxnId(),
                    "submerchant_id", testSubMerchantId,
                    "label", "sm_" + testSubMerchantId,
                    "amount", "970.00",
                    "peb_service_charge", "15.00",
                    "peb_service_tax", "5.00"
                ))
            ));

        Map<String, Object> result = reconciliationService.reconcileSplits("2026-09-09");

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("splitMatched"));
        assertEquals(0, result.get("splitMismatches"));
    }

    @Test
    void testSplitReconciliationDetectsMissingSettlement() {
        Bill bill = createPaidBill("KB_RECON_MISSING", new BigDecimal("1000.00"), new BigDecimal("50.00"));

        mockSubMerchant(testSubMerchantId);
        // Settlement rows exist but for a DIFFERENT transaction — our bill is absent
        when(easebuzzApi.retrieveSettlements(any()))
            .thenReturn(Map.of(
                "status", true,
                "data", List.of(Map.of(
                    "txnid", "SOME_OTHER_TXN",
                    "submerchant_id", testSubMerchantId,
                    "label", "sm_" + testSubMerchantId,
                    "payout_amount", "950.00"
                ))
            ));

        Map<String, Object> result = reconciliationService.reconcileSplits("2026-09-09");

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("splitMissingSettlements"));
    }

    private Bill createPaidBill(String txnid, BigDecimal total, BigDecimal commission) {
        Bill bill = new Bill();
        bill.setRestaurantId(testRestaurantId);
        bill.setLocalId(1L);
        bill.setDeviceId("TEST_DEVICE");
        bill.setDailyOrderId(1L);
        bill.setDailyOrderDisplay("001");
        bill.setLifetimeOrderId(1L);
        bill.setOrderType("DINE_IN");
        bill.setCustomerName("Test Customer");
        bill.setSubtotal(total);
        bill.setTotalAmount(total);
        bill.setPaymentMode("ONLINE");
        bill.setPaymentStatus("paid");
        bill.setOrderStatus("completed");
        bill.setGatewayTxnId(txnid);
        bill.setCommissionAmount(commission);
        bill.setSettledAt(LocalDate.of(2026, 9, 9).atTime(12, 0)
                .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli());
        bill.setLastResetDate("2026-09-09");
        bill.setCreatedAt(System.currentTimeMillis());
        bill.setUpdatedAt(System.currentTimeMillis());
        return billRepo.save(bill);
    }

    private void mockSubMerchant(String subMerchantId) {
        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setRestaurantId(testRestaurantId);
        sm.setBusinessName("Test Restaurant");
        sm.setSubMerchantId(subMerchantId);
        sm.setStatus("ACTIVE");
        sm.setSplitLabel("sm_" + subMerchantId);
        sm.setCommissionRate(new BigDecimal("5.0"));
        sm.setCreatedAt(System.currentTimeMillis());
        sm.setUpdatedAt(System.currentTimeMillis());
        subMerchantRepo.save(sm);
        when(subMerchantService.getByRestaurantId(testRestaurantId)).thenReturn(sm);
    }

    private void mockSettlements(String txnid, String payoutAmount, String label) {
        when(easebuzzApi.retrieveSettlements(any()))
            .thenReturn(Map.of(
                "status", true,
                "data", List.of(Map.of(
                    "txnid", txnid,
                    "submerchant_id", testSubMerchantId,
                    "label", label,
                    "payout_amount", payoutAmount
                ))
            ));
    }
}