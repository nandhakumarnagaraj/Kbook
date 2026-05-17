package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EasebuzzNewFeaturesTest extends BaseIntegrationTest {

    @Autowired private SubMerchantService subMerchantService;
    @Autowired private EasebuzzSubMerchantRepository subMerchantRepo;
    
    @MockBean private EasebuzzApiClient easebuzzApi;

    private Long testRestaurantId;
    private String testSubMerchantId;

    @BeforeEach
    void setup() {
        testRestaurantId = 800L + System.currentTimeMillis() % 100;
        testSubMerchantId = "S360NEW" + System.currentTimeMillis();
        persistUser("admin" + System.currentTimeMillis() + "@kbook.com", testRestaurantId, UserRole.KBOOK_ADMIN);
    }

    @Transactional
    @Test
    void testOtpFlow() {
        EasebuzzSubMerchant sm = createPendingSubMerchant();

        // 1. Verify OTP (mock)
        when(easebuzzApi.verifyOtp(eq(testSubMerchantId), eq("123456")))
            .thenReturn(Map.of("status", "success", "msg", "OTP verified successfully"));

        Map<String, Object> verifyResult = subMerchantService.verifyOtp(sm.getId(), "123456");
        assertEquals("success", verifyResult.get("status"));

        // 2. Resend OTP (mock)
        when(easebuzzApi.resendOtp(eq(testSubMerchantId)))
            .thenReturn(Map.of("status", "success", "msg", "OTP resent successfully"));

        Map<String, Object> resendResult = subMerchantService.resendOtp(sm.getId());
        assertEquals("success", resendResult.get("status"));
    }

    @Test
    void testSettlementAndPayout() {
        // 1. On-demand settlement (mock)
        when(easebuzzApi.initiateOnDemandSettlement(anyString(), eq("1000.00")))
            .thenReturn(Map.of("status", "success", "msg", "Settlement initiated"));

        Map<String, Object> settleResult = subMerchantService.initiateOnDemandSettlement("1000.00");
        assertEquals("success", settleResult.get("status"));

        // 2. Payout V2 (mock)
        Map<String, String> beneficiary = Map.of(
            "beneficiary_name", "Test User",
            "beneficiary_account_number", "1234567890",
            "beneficiary_ifsc", "HDFC0000123"
        );
        when(easebuzzApi.initiatePayout(anyString(), eq("500.00"), eq(beneficiary)))
            .thenReturn(Map.of("status", "success", "msg", "Payout initiated"));

        Map<String, Object> payoutResult = subMerchantService.initiatePayout("500.00", beneficiary);
        assertEquals("success", payoutResult.get("status"));
        
        // 3. Retrieve settlements (mock)
        when(easebuzzApi.retrieveSettlements(eq("2024-05-17")))
            .thenReturn(Map.of("status", "success", "data", java.util.Collections.emptyList()));

        Map<String, Object> retrieveResult = subMerchantService.retrieveSettlements("2024-05-17");
        assertEquals("success", retrieveResult.get("status"));
    }

    private EasebuzzSubMerchant createPendingSubMerchant() {
        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setRestaurantId(testRestaurantId);
        sm.setBusinessName("New Restaurant");
        sm.setSubMerchantId(testSubMerchantId);
        sm.setStatus("PENDING_KYC");
        sm.setCreatedAt(System.currentTimeMillis());
        sm.setUpdatedAt(System.currentTimeMillis());
        return subMerchantRepo.save(sm);
    }
}
