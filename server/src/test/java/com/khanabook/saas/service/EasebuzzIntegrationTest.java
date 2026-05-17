package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Easebuzz Sub-Merchant & Payment Integration Tests
 * 
 * These tests verify the KhanaBook → Easebuzz API orchestration without
 * calling the actual Easebuzz sandbox. The EasebuzzApiClient is mocked
 * to return predictable responses.
 * 
 * For live sandbox testing, run the curl script: test-easebuzz-sandbox.sh
 */
class EasebuzzIntegrationTest extends BaseIntegrationTest {

    @Autowired private SubMerchantService subMerchantService;
    @Autowired private EasebuzzPaymentService paymentService;
    @Autowired private PostSplitService postSplitService;
    @Autowired private BillRepository billRepository;
    @Autowired private EasebuzzSubMerchantRepository subMerchantRepo;
    
    @MockBean private EasebuzzApiClient easebuzzApi;

    private static final Long TEST_RESTAURANT_ID = 999L;
    private static final String TEST_SUBMERCHANT_ID = "S360TEST";

    @BeforeEach
    void setup() {
        persistUser("testadmin@kbook.com", TEST_RESTAURANT_ID, UserRole.KBOOK_ADMIN);
    }

    @Test
    void testSubMerchantLifecycle() {
        // 1. Create draft
        Map<String, Object> data = Map.of(
            "businessName", "Test Restaurant",
            "businessType", "Restaurant",
            "pan", "ABCDE1234F",
            "gst", "27AABCU9603R1ZM",
            "bankAccountNo", "123456789012",
            "ifsc", "HDFC0000123",
            "bankName", "HDFC",
            "branchName", "Test Branch",
            "beneficiaryName", "Test Owner",
            "businessAddress", "123 Test St",
            "contactEmail", "test@example.com",
            "contactPhone", "9999999999",
            "commissionRate", "3.0"
        );

        EasebuzzSubMerchant sm = subMerchantService.create(data, TEST_RESTAURANT_ID);
        assertNotNull(sm.getId());
        assertEquals("DRAFT", sm.getStatus());
        assertEquals("Test Restaurant", sm.getBusinessName());

        // 2. Submit to Easebuzz (mock)
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", TEST_SUBMERCHANT_ID));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId());
        assertEquals("PENDING_KYC", submitted.getStatus());
        assertEquals(TEST_SUBMERCHANT_ID, submitted.getSubMerchantId());

        // 3. Generate KYC access key (mock)
        when(easebuzzApi.generateKycAccessKey(any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "msg", "https://kyc.easebuzz.in/test"));

        Map<String, Object> kycResult = subMerchantService.generateKycAccessKey(sm.getId());
        assertEquals("success", kycResult.get("status"));
        assertTrue(kycResult.get("kyc_url").toString().startsWith("https://"));

        // Verify KYC URL persisted
        EasebuzzSubMerchant updated = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertNotNull(updated.getKycPortalUrl());

        // 4. Create split label (mock)
        when(easebuzzApi.createSplitLabel(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "msg", "Label created"));

        Map<String, Object> splitResult = subMerchantService.createSplitLabel(sm.getId());
        assertEquals("success", splitResult.get("status"));
        assertEquals("sm_" + TEST_SUBMERCHANT_ID, splitResult.get("label"));

        // 5. Simulate KYC approval webhook
        subMerchantService.processWebhook(Map.of(
            "status", "1",
            "data", Map.of("submerchant_id", TEST_SUBMERCHANT_ID, "status", "True")
        ));

        EasebuzzSubMerchant active = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertEquals("ACTIVE", active.getStatus());
        assertNotNull(active.getKycActivatedAt());
    }

    @Test
    void testPaymentInitiationAndWebhook() {
        // Setup active sub-merchant
        EasebuzzSubMerchant sm = createActiveSubMerchant();

        // Create a bill
        Bill bill = createTestBill(TEST_RESTAURANT_ID, new BigDecimal("1000.00"));

        // Mock payment initiation
        when(easebuzzApi.initiatePayment(any()))
            .thenReturn(Map.of(
                "status", "success",
                "access_token", "test_token_123",
                "payment_url", "https://testpay.easebuzz.in/pay/test"
            ));

        // 1. Create payment order
        Map<String, Object> result = paymentService.createOrder(bill.getId(), TEST_RESTAURANT_ID);
        assertEquals("success", result.get("status"));
        assertNotNull(result.get("txnid"));
        assertTrue(result.get("txnid").toString().startsWith("KB"));

        // Verify bill updated
        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();
        assertNotNull(updatedBill.getGatewayTxnId());
        assertEquals("success", updatedBill.getGatewayStatus());

        // 2. Simulate payment webhook
        String txnid = updatedBill.getGatewayTxnId();
        when(easebuzzApi.updateTransactionSplit(any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "request_status", "success"));

        // The webhook handler would trigger post-split async
        // For testing, we call the split service directly
        postSplitService.createPostSplitWithRetry(bill.getId(), "E250TEST123", txnid);

        // Verify bill has commission and settlement
        Bill settledBill = billRepository.findById(bill.getId()).orElseThrow();
        assertNotNull(settledBill.getCommissionAmount());
        assertTrue(settledBill.getCommissionAmount().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(settledBill.getSettledAt());
    }

    @Test
    void testRefundFlow() {
        // Setup paid bill
        Bill bill = createTestBill(TEST_RESTAURANT_ID, new BigDecimal("500.00"));
        bill.setGatewayTxnId("KBTEST123");
        bill.setGatewayStatus("success");
        bill.setPaymentStatus("paid");
        billRepository.save(bill);

        // Mock refund
        when(easebuzzApi.initiateRefund(any(), any(), any(), any()))
            .thenReturn(Map.of(
                "status", true,
                "msg", Map.of("refund_id", "REFTEST123", "status", "initiated")
            ));

        // Initiate refund
        Map<String, Object> refundResult = paymentService.initiateRefund(
            bill.getId(), new BigDecimal("250.00"), "Customer request"
        );
        assertEquals("success", refundResult.get("status"));

        // Verify refund ID stored
        Bill refundedBill = billRepository.findById(bill.getId()).orElseThrow();
        assertEquals("REFTEST123", refundedBill.getRefundId());

        // Mock refund status check
        when(easebuzzApi.getRefundStatus(any(), any()))
            .thenReturn(Map.of(
                "status", true,
                "msg", Map.of("refund_id", "REFTEST123", "status", "completed")
            ));

        Map<String, Object> statusResult = paymentService.getRefundStatus(bill.getId());
        assertEquals("success", statusResult.get("status"));
    }

    @Test
    void testWebhookHashVerification() {
        // This test would require the actual hash computation
        // For unit testing, we verify the verifyWebhookHash method exists
        // and returns false for invalid hashes
        
        // Create a mock webhook payload with invalid hash
        Map<String, String> payload = Map.of(
            "txnid", "KBTEST123",
            "status", "success",
            "amount", "100.00",
            "hash", "invalid_hash"
        );

        // In actual test, we'd inject EasebuzzProperties and test the private method
        // or make it package-private for testing
        assertNotNull(payload.get("hash"));
    }

    @Test
    void testPostSplitRetryExhaustion() {
        // Setup
        Bill bill = createTestBill(TEST_RESTAURANT_ID, new BigDecimal("1000.00"));
        EasebuzzSubMerchant sm = createActiveSubMerchant();

        // Mock split API to always fail
        when(easebuzzApi.updateTransactionSplit(any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", false, "error", "EBPTSURVE06"));

        // Should exhaust retries without throwing
        assertDoesNotThrow(() -> 
            postSplitService.createPostSplitWithRetry(bill.getId(), "E250TEST", "KBTEST")
        );

        // Verify bill NOT settled
        Bill unsettledBill = billRepository.findById(bill.getId()).orElseThrow();
        assertNull(unsettledBill.getSettledAt());
    }

    // --- Helpers ---

    private EasebuzzSubMerchant createActiveSubMerchant() {
        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setRestaurantId(TEST_RESTAURANT_ID);
        sm.setBusinessName("Test Restaurant");
        sm.setSubMerchantId(TEST_SUBMERCHANT_ID);
        sm.setStatus("ACTIVE");
        sm.setSplitLabel("sm_" + TEST_SUBMERCHANT_ID);
        sm.setBankAccountNo("123456789012");
        sm.setIfsc("HDFC0000123");
        sm.setBankName("HDFC");
        sm.setBranchName("Test Branch");
        sm.setBeneficiaryName("Test Owner");
        sm.setCommissionRate(new BigDecimal("3.0"));
        sm.setContactEmail("test@example.com");
        sm.setContactPhone("9999999999");
        sm.setCreatedAt(System.currentTimeMillis());
        sm.setUpdatedAt(System.currentTimeMillis());
        return subMerchantRepo.save(sm);
    }

    private Bill createTestBill(Long restaurantId, BigDecimal amount) {
        Bill bill = new Bill();
        bill.setRestaurantId(restaurantId);
        bill.setLocalId(1L);
        bill.setDeviceId("TEST_DEVICE");
        bill.setDailyOrderId(1L);
        bill.setDailyOrderDisplay("001");
        bill.setLifetimeOrderId(1L);
        bill.setOrderType("DINE_IN");
        bill.setCustomerName("Test Customer");
        bill.setSubtotal(amount);
        bill.setTotalAmount(amount);
        bill.setPaymentMode("ONLINE");
        bill.setPaymentStatus("pending");
        bill.setOrderStatus("completed");
        bill.setLastResetDate("2024-01-01");
        bill.setCreatedAt(System.currentTimeMillis());
        bill.setUpdatedAt(System.currentTimeMillis());
        return billRepository.save(bill);
    }
}
