package com.khanabook.saas.service;

import com.khanabook.saas.feature.payments.service.EasebuzzApiClient;
import com.khanabook.saas.feature.payments.service.EasebuzzWebhookService;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.payments.service.EasebuzzProperties;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EasebuzzNewFeaturesTest extends BaseIntegrationTest {

    @Autowired private SubMerchantService subMerchantService;
    @Autowired private EasebuzzSubMerchantRepository subMerchantRepo;
    
    @MockBean private EasebuzzApiClient easebuzzApi;

    @Autowired private EasebuzzWebhookService webhookService;
    @Autowired private EasebuzzProperties easebuzzProps;

    private Long testRestaurantId;
    private String testSubMerchantId;

    @BeforeEach
    void setup() {
        testRestaurantId = 800L + System.currentTimeMillis() % 100;
        testSubMerchantId = "S360NEW" + System.currentTimeMillis();
        persistUser("admin" + System.currentTimeMillis() + "@kbook.com", testRestaurantId, UserRole.KBOOK_ADMIN);
    }

    @Test
    void testOnDemandSettlementAndRetrieval() {
        // 1. On-demand settlement (mock): a batch of transactions, each with its own hash.
        when(easebuzzApi.initiateOnDemandSettlement(anyString(), any(), anyList()))
            .thenReturn(Map.of("status", true, "batch_status", "pending"));

        Map<String, Object> settleResult = subMerchantService.initiateOnDemandSettlement(
            null, "test batch",
            java.util.List.of(Map.of(
                "easebuzz_id", "S2610TEST",
                "merchant_txn_id", "KB_TXN_1",
                "amount", "1000.00")));
        assertEquals("pending", settleResult.get("batch_status"));

        // 2. Retrieve settlements (mock)
        when(easebuzzApi.retrieveSettlements(eq("2024-05-17")))
            .thenReturn(Map.of("status", "success", "data", java.util.Collections.emptyList()));

        Map<String, Object> retrieveResult = subMerchantService.retrieveSettlements("2024-05-17");
        assertEquals("success", retrieveResult.get("status"));
    }

    @Transactional
    @Test
    void testSetAndClearSplitLabel() {
        EasebuzzSubMerchant sm = createPendingSubMerchant();
        sm.setBankAccountNo("1234567890");
        sm.setIfsc("HDFC0001234");
        subMerchantRepo.save(sm);

        Map<String, Object> armed = subMerchantService.setSplitLabel(sm.getId(), "  sm_" + testSubMerchantId + "  ");
        assertEquals("success", armed.get("status"));
        assertEquals(true, armed.get("postSplitArmed"));

        EasebuzzSubMerchant stored = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertEquals("sm_" + testSubMerchantId, stored.getSplitLabel());
        assertEquals("1234567890|HDFC0001234", stored.getSplitLabelBankSnapshot());

        Map<String, Object> cleared = subMerchantService.setSplitLabel(sm.getId(), "   ");
        assertEquals(false, cleared.get("postSplitArmed"));
        EasebuzzSubMerchant afterClear = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertNull(afterClear.getSplitLabel());
        assertNull(afterClear.getSplitLabelBankSnapshot());
    }

    @Test
    void testSetSplitLabelRejectsCharactersEasebuzzCannotCarry() {
        EasebuzzSubMerchant sm = createPendingSubMerchant();
        assertThrows(com.khanabook.saas.core.exception.BusinessRuleException.class,
            () -> subMerchantService.setSplitLabel(sm.getId(), "sm 1|bad"));
    }

    @Test
    void testSubMerchantWebhookHashVerification_ValidHash() {
        // Compute a valid hash: key|submerchant_id|salt
        String submerchantId = "S360_TEST_001";
        String hashInput = easebuzzProps.getMerchantKey() + "|" + submerchantId + "|" + easebuzzProps.getSalt();
        String validHash = sha512Hex(hashInput);

        // Build payload matching ERA's documented format
        Map<String, Object> data = new HashMap<>();
        data.put("hash", validHash);
        data.put("submerchant_id", submerchantId);
        data.put("status", "True");

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "1");
        payload.put("data", data);

        Map<String, Object> result = webhookService.handleSubMerchantWebhook(payload);
        assertEquals("received", result.get("status"), "Valid hash should be accepted");
    }

    @Test
    void testSubMerchantWebhookHashVerification_InvalidHash() {
        // Build payload with wrong hash
        Map<String, Object> data = new HashMap<>();
        data.put("hash", "invalid_hash_value");
        data.put("submerchant_id", "S360_TEST_002");
        data.put("status", "True");

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "1");
        payload.put("data", data);

        Map<String, Object> result = webhookService.handleSubMerchantWebhook(payload);
        assertEquals("hash_mismatch", result.get("status"), "Invalid hash should be rejected");
    }

    @Test
    void testSubMerchantWebhookHashVerification_MissingData() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "1");
        // No 'data' field

        Map<String, Object> result = webhookService.handleSubMerchantWebhook(payload);
        assertEquals("hash_mismatch", result.get("status"), "Missing data should be rejected");
    }

    @Transactional
    @Test
    void testSubMerchantWebhook_MerchantKycApprovalEvent() {
        EasebuzzSubMerchant sm = createPendingSubMerchant();

        Map<String, Object> data = new HashMap<>();
        data.put("id", testSubMerchantId);
        data.put("kyc_status", true);
        data.put("kyc_profile_status", "Completed");
        data.put("email", sm.getContactEmail());
        // The documented KYC callback is hash-signed; without it the webhook is indistinguishable
        // from an unauthenticated POST that names a known sub-merchant.
        data.put("hash", sha512Hex(easebuzzProps.getMerchantKey() + "|" + testSubMerchantId + "|"
                + easebuzzProps.getSalt()));
        data.put("virtual_account", Map.of(
            "account_number", "10100000001234",
            "ifsc", "ICIC0000104",
            "status", "active"
        ));

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "MERCHANT_KYC_APPROVAL");
        payload.put("data", data);

        Map<String, Object> result = webhookService.handleSubMerchantWebhook(payload);
        assertEquals("received", result.get("status"));

        EasebuzzSubMerchant updated = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertEquals("ACTIVE", updated.getStatus());
        assertEquals("10100000001234", updated.getVirtualAccountNumber());
    }

    private String sha512Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte b : digest) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
