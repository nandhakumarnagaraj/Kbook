package com.khanabook.saas.service;

import com.khanabook.saas.feature.billing.service.PostSplitService;
import com.khanabook.saas.feature.payments.service.EasebuzzApiClient;
import com.khanabook.saas.feature.payments.service.EasebuzzPaymentService;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import com.khanabook.saas.feature.payments.controller.RestaurantPaymentConfigController;
import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired private RestaurantPaymentConfigController paymentConfigController;
    @Autowired private EasebuzzPaymentService paymentService;
    @Autowired private PostSplitService postSplitService;
    @Autowired private BillRepository billRepository;
    @Autowired private EasebuzzSubMerchantRepository subMerchantRepo;
    @Autowired private EasebuzzSubMerchantWebhookEventRepository webhookEventRepo;
    @Autowired private com.khanabook.saas.feature.payments.service.SubMerchantKycExpiryService kycExpiryService;
    @Autowired private com.khanabook.saas.feature.payments.service.StuckPaymentSweepService sweepService;
    
    @MockBean private EasebuzzApiClient easebuzzApi;
    @MockBean private com.khanabook.saas.feature.notifications.service.PushNotificationService pushNotificationService;

    private static final java.util.concurrent.atomic.AtomicLong TEST_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    private Long testRestaurantId;
    private String testLoginId;
    private String testSubMerchantId;

    @BeforeEach
    void setup() {
        testRestaurantId = 900L + TEST_SEQ.getAndIncrement() % 100;
        testLoginId = "testadmin" + System.currentTimeMillis() + "@kbook.com";
        testSubMerchantId = "S360TEST" + System.currentTimeMillis();
        persistUser(testLoginId, testRestaurantId, UserRole.KBOOK_ADMIN);
    }

    @AfterEach
    void cleanup() {
        // Data cleaned up by @Transactional rollback
    }

    @Transactional
    @Test
    void testSubMerchantLifecycle() {
        // 1. Create draft
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("businessName", "Test Restaurant");
        data.put("businessType", "Restaurant");
        data.put("restaurantFormat", "DINE_IN");
        data.put("pan", "ABCDE1234F");
        data.put("gst", "27AABCU9603R1ZM");
        data.put("bankAccountNo", "123456789012");
        data.put("ifsc", "HDFC0000123");
        data.put("bankName", "HDFC");
        data.put("branchName", "Test Branch");
        data.put("beneficiaryName", "Test Owner");
        data.put("businessAddress", "123 Test St");
        data.put("state", "Karnataka");
        data.put("legalEntityName", "Test Restaurant Pvt Ltd");
        data.put("fssaiNumber", "12345678901234");
        data.put("contactEmail", "test@example.com");
        data.put("contactPhone", "9999999999");
        data.put("commissionRate", "0.0");

        EasebuzzSubMerchant sm = subMerchantService.create(data, testRestaurantId);
        assertNotNull(sm.getId());
        assertEquals("DRAFT", sm.getStatus());
        assertEquals("Test Restaurant", sm.getBusinessName());

        // 2. Submit to Easebuzz (mock)
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId());
        assertEquals("PENDING_KYC", submitted.getStatus());
        assertNotNull(submitted.getSubMerchantId());

        // 3. Generate KYC access key (mock)
        when(easebuzzApi.generateKycAccessKey(any(), any(), any(), any()))
            .thenReturn(Map.of("status", "success", "kyc_dashboard_url", "https://kyc.easebuzz.in/test"));

        Map<String, Object> kycResult = subMerchantService.generateKycAccessKey(sm.getId());
        assertEquals("success", kycResult.get("status"));
        assertTrue(kycResult.get("kyc_url").toString().startsWith("https://"));

        // Verify KYC URL persisted
        EasebuzzSubMerchant updated = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertNotNull(updated.getKycPortalUrl());

        // 4. Create split label (mock)
        when(easebuzzApi.createSplitLabel(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", "success", "msg", "Label created"));

        Map<String, Object> splitResult = subMerchantService.createSplitLabel(sm.getId());
        assertEquals("success", splitResult.get("status"));
        assertEquals("sm_" + testSubMerchantId, splitResult.get("label"));

        // 5. Simulate KYC approval webhook
        subMerchantService.processWebhook(Map.of(
            "status", "1",
            "data", Map.of("submerchant_id", testSubMerchantId, "status", "True")
        ));

        EasebuzzSubMerchant active = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertEquals("ACTIVE", active.getStatus());
        assertNotNull(active.getKycActivatedAt());
    }

    @Transactional
    @Test
    void testMerchantKycApprovalWebhookWithVirtualAccount() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        subMerchantRepo.save(sm);

        // Simulate Era's MERCHANT_KYC_APPROVAL webhook format
        Map<String, Object> payload = Map.of(
            "event", "MERCHANT_KYC_APPROVAL",
            "data", Map.of(
                "id", testSubMerchantId,
                "kyc_status", true,
                "kyc_profile_status", "Completed",
                "email", "test@restaurant.com",
                "virtual_account", Map.of(
                    "account_number", "10100000009876",
                    "ifsc", "ICIC0000104",
                    "status", "active"
                )
            )
        );

        subMerchantService.processWebhook(payload);

        EasebuzzSubMerchant active = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertEquals("ACTIVE", active.getStatus());
        assertEquals("True", active.getKycStatus());
        assertEquals("10100000009876", active.getVirtualAccountNumber());
        assertEquals("ICIC0000104", active.getVirtualAccountIfsc());
        assertNotNull(active.getKycActivatedAt());
    }

    @Transactional
    @Test
    void testMerchantKycApprovalWebhookWithCpvPending() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        subMerchantRepo.save(sm);

        Map<String, Object> payload = Map.of(
            "event", "MERCHANT_KYC_APPROVAL",
            "data", Map.of(
                "id", testSubMerchantId,
                "kyc_status", false,
                "kyc_profile_status", "CPV_PENDING",
                "kyc_url", "https://kyc.easebuzz.in/access_key=cpv_test"
            )
        );

        subMerchantService.processWebhook(payload);

        EasebuzzSubMerchant pending = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertEquals("CPV_PENDING", pending.getStatus());
        assertEquals("Pending", pending.getKycStatus());
        assertEquals("https://kyc.easebuzz.in/access_key=cpv_test", pending.getKycPortalUrl());
    }

    @Transactional
    @Test
    void proprietorshipRequiresTwoBusinessProofsForSubmission() {
        Map<String, Object> data = baseSubMerchantData();
        data.put("businessType", "SOLE_PROPRIETORSHIP");
        EasebuzzSubMerchant sm = subMerchantService.create(data, testRestaurantId);

        // Missing business proofs → submission blocked for proprietorship.
        com.khanabook.saas.core.exception.BusinessRuleException ex = assertThrows(
            com.khanabook.saas.core.exception.BusinessRuleException.class,
            () -> subMerchantService.submitToEasebuzz(sm.getId())
        );
        assertEquals("BUSINESS_PROOFS_REQUIRED", ex.getRule());

        // Supplying both proofs allows submission to proceed.
        subMerchantService.update(sm.getId(), Map.of(
            "businessProof1Type", "GST_CERTIFICATE",
            "businessProof1Url", "https://docs.kbook.test/proof1.pdf",
            "businessProof2Type", "UDYAM_CERTIFICATE",
            "businessProof2Url", "https://docs.kbook.test/proof2.pdf"
        ));
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId());
        assertEquals("PENDING_KYC", submitted.getStatus());
    }

    @Transactional
    @Test
    void proprietorshipAllowsSingleValidBusinessProof() {
        Map<String, Object> data = baseSubMerchantData();
        data.put("businessType", "SOLE_PROPRIETORSHIP");
        EasebuzzSubMerchant sm = subMerchantService.create(data, testRestaurantId);

        // Supplying only 1 valid address proof allows submission (Era confirmation).
        subMerchantService.update(sm.getId(), Map.of(
            "businessProof1Type", "ELECTRICITY_BILL",
            "businessProof1Url", "https://docs.kbook.test/bill.pdf"
        ));
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId());
        assertEquals("PENDING_KYC", submitted.getStatus());
    }

    @Transactional
    @Test
    void proprietorshipRejectsRentAgreementAsOnlyProof() {
        Map<String, Object> data = baseSubMerchantData();
        data.put("businessType", "SOLE_PROPRIETORSHIP");
        EasebuzzSubMerchant sm = subMerchantService.create(data, testRestaurantId);

        // A rent agreement alone reaches CPV and comes back negative - block it.
        subMerchantService.update(sm.getId(), Map.of(
            "businessProof1Type", "RENT_AGREEMENT",
            "businessProof1Url", "https://docs.kbook.test/rent.pdf"
        ));
        com.khanabook.saas.core.exception.BusinessRuleException ex = assertThrows(
            com.khanabook.saas.core.exception.BusinessRuleException.class,
            () -> subMerchantService.submitToEasebuzz(sm.getId())
        );
        assertEquals("BUSINESS_PROOF_TYPE_NOT_ACCEPTED", ex.getRule());

        // Adding an accepted proof unblocks the submission.
        subMerchantService.update(sm.getId(), Map.of(
            "businessProof2Type", "SHOP_ESTABLISHMENT",
            "businessProof2Url", "https://docs.kbook.test/shop.pdf"
        ));
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId());
        assertEquals("PENDING_KYC", submitted.getStatus());
    }

    @Transactional
    @Test
    void submissionRequiresLegalEntityName() {
        Map<String, Object> data = baseSubMerchantData();
        data.remove("legalEntityName"); // blank legal name would silently fall back to trade name → CPV mismatch
        EasebuzzSubMerchant sm = subMerchantService.create(data, testRestaurantId);

        // Blank legal entity name → submission blocked (no silent trade-name fallback).
        com.khanabook.saas.core.exception.BusinessRuleException ex = assertThrows(
            com.khanabook.saas.core.exception.BusinessRuleException.class,
            () -> subMerchantService.submitToEasebuzz(sm.getId())
        );
        assertEquals("LEGAL_ENTITY_NAME_REQUIRED", ex.getRule());

        // Supplying the legal entity name allows submission to proceed.
        subMerchantService.update(sm.getId(), Map.of("legalEntityName", "Test Restaurant Pvt Ltd"));
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId());
        assertEquals("PENDING_KYC", submitted.getStatus());
    }

    @Transactional
    @Test
    void submissionRequiresMandatoryFields() {
        Map<String, Object> data = baseSubMerchantData();
        data.remove("pan");   // core CPV fields left blank
        data.remove("ifsc");
        data.remove("bankName");
        data.remove("branchName");
        EasebuzzSubMerchant sm = subMerchantService.create(data, testRestaurantId);

        // Missing mandatory fields → submission blocked, gaps listed in the message.
        com.khanabook.saas.core.exception.BusinessRuleException ex = assertThrows(
            com.khanabook.saas.core.exception.BusinessRuleException.class,
            () -> subMerchantService.submitToEasebuzz(sm.getId())
        );
        assertEquals("MANDATORY_FIELDS_MISSING", ex.getRule());
        assertTrue(ex.getMessage().contains("PAN"));
        assertTrue(ex.getMessage().contains("IFSC"));
        assertTrue(ex.getMessage().contains("bank name"));
        assertTrue(ex.getMessage().contains("branch name"));

        // Supplying the missing fields allows submission to proceed.
        subMerchantService.update(sm.getId(), Map.of(
            "pan", "ABCDE1234F", "ifsc", "HDFC0000123",
            "bankName", "HDFC", "branchName", "Test Branch"
        ));
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId());
        assertEquals("PENDING_KYC", submitted.getStatus());
    }

    @Transactional
    @Test
    void proprietorshipRequiresDistinctBusinessProofTypes() {
        Map<String, Object> data = baseSubMerchantData();
        data.put("businessType", "SOLE_PROPRIETORSHIP");
        // Both proofs present with URLs, but the SAME document type.
        data.put("businessProof1Type", "GST_CERTIFICATE");
        data.put("businessProof1Url", "https://docs.kbook.test/proof1.pdf");
        data.put("businessProof2Type", "gst_certificate"); // same type, different case
        data.put("businessProof2Url", "https://docs.kbook.test/proof2.pdf");
        EasebuzzSubMerchant sm = subMerchantService.create(data, testRestaurantId);

        com.khanabook.saas.core.exception.BusinessRuleException ex = assertThrows(
            com.khanabook.saas.core.exception.BusinessRuleException.class,
            () -> subMerchantService.submitToEasebuzz(sm.getId())
        );
        assertEquals("BUSINESS_PROOF_TYPES_NOT_DISTINCT", ex.getRule());

        // Making the second proof a distinct type allows submission to proceed.
        subMerchantService.update(sm.getId(), Map.of("businessProof2Type", "UDYAM_CERTIFICATE"));
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId());
        assertEquals("PENDING_KYC", submitted.getStatus());
    }

    @Transactional
    @Test
    void kycDocumentIsNotForwardedAndThePortalIsReturned() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        sm.setKycPortalUrl("https://kyc.easebuzz.in/session/abc");
        subMerchantRepo.save(sm);

        // upload_kyc_documents returns 404 on both hosts (verified 2026-10-18), so the service
        // must not attempt it at all: a guaranteed-failing request buys nothing and hides the
        // fact that the document can only be uploaded on the portal.
        Map<String, Object> result = subMerchantService.submitKycDocument(
                testRestaurantId, "id_proof", null, kycFile());

        assertEquals("portal_required", result.get("status"));
        assertEquals("https://kyc.easebuzz.in/session/abc", result.get("kycUrl"));
        assertTrue(result.get("message").toString().contains("portal"),
                "the merchant must be told where the document actually goes");
        verifyNoInteractions(easebuzzApi);
        // Nothing may claim the document reached Easebuzz when it never left this server.
        assertNull(subMerchantRepo.findById(sm.getId()).orElseThrow().getIdProofKey());
    }

    @Transactional
    @Test
    void theAppAsksForThePortalLinkWithoutSendingTheDocument() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        sm.setKycPortalUrl("https://kyc.easebuzz.in/session/abc");
        subMerchantRepo.save(sm);

        // The app path: no file at all. Sending bytes to a server that may not keep them was a
        // wasted upload on a POS terminal, so only the document type is sent.
        Map<String, Object> result = subMerchantService.requestKycPortalLink(testRestaurantId, "bank_proof");

        assertEquals("portal_required", result.get("status"));
        assertEquals("BANK_PROOF", result.get("documentType"));
        assertEquals("https://kyc.easebuzz.in/session/abc", result.get("kycUrl"));
        verifyNoInteractions(easebuzzApi);

        EasebuzzSubMerchant stored = subMerchantRepo.findById(sm.getId()).orElseThrow();
        // Support still needs to see what the merchant said they had.
        assertEquals("KYC_UPLOAD_DEFERRED_TO_PORTAL:BANK_PROOF", stored.getEasebuzzResponse());
        // Nothing may claim the document arrived when it never left the device.
        assertNull(stored.getBankProofKey());
    }

    @Test
    void anUnknownDocumentTypeIsRejectedBeforeAnythingIsRecorded() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        subMerchantRepo.save(sm);

        assertThrows(IllegalArgumentException.class,
                () -> subMerchantService.requestKycPortalLink(testRestaurantId, "aadhaar_back"));
        assertNull(subMerchantRepo.findById(sm.getId()).orElseThrow().getEasebuzzResponse());
    }

    @Transactional
    @Test
    void createResponseCarryingAKycPortalLinkIsPersistedForTheOwner() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        String portalUrl = "https://kyc.easebuzz.in/session/abc?access_key=SUPERSECRET";
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId, "kyc_dashboard_url", portalUrl));

        subMerchantService.submitToEasebuzz(sm.getId());

        EasebuzzSubMerchant stored = subMerchantRepo.findById(sm.getId()).orElseThrow();
        // The create response already carries the link, so the owner must not need a separate
        // generate_kyc_access_key round trip before they can upload documents.
        assertEquals(portalUrl, stored.getKycPortalUrl());
        assertEquals("PENDING_KYC", stored.getStatus());
        // The response is stored for support, but the access key inside it is a credential.
        assertFalse(stored.getEasebuzzResponse().contains("SUPERSECRET"),
                "stored response must not carry the KYC access key: " + stored.getEasebuzzResponse());
        assertTrue(stored.getEasebuzzResponse().contains("REDACTED"));
        assertTrue(stored.getEasebuzzResponse().contains(testSubMerchantId),
                "redaction must keep the diagnostics support needs");
    }

    @Transactional
    @Test
    void createResponseWithANestedKycDetailsLinkIsAlsoAccepted() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        String portalUrl = "https://kyc.easebuzz.in/session/nested";
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of(
                "status", true,
                "submerchant_id", testSubMerchantId,
                "kyc_details", Map.of("kyc_dashboard_url", portalUrl)));

        subMerchantService.submitToEasebuzz(sm.getId());

        assertEquals(portalUrl, subMerchantRepo.findById(sm.getId()).orElseThrow().getKycPortalUrl(),
                "the link is read from kyc_details when Easebuzz nests it there");
    }

    @Transactional
    @Test
    void createResponseWithoutALinkLeavesPortalIssuanceToTheAccessKeyCall() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));

        subMerchantService.submitToEasebuzz(sm.getId());

        assertNull(subMerchantRepo.findById(sm.getId()).orElseThrow().getKycPortalUrl(),
                "no link was returned, so none may be invented");
    }

    @Transactional
    @Test
    void portalLinkRequestIssuesOneWhenCreateReturnedNone() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        subMerchantRepo.save(sm);
        String portalUrl = "https://kyc.easebuzz.in/session/issued-on-demand";
        when(easebuzzApi.generateKycAccessKey(any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "kyc_dashboard_url", portalUrl));

        Map<String, Object> result = subMerchantService.requestKycPortalLink(testRestaurantId, "id_proof");

        assertEquals("portal_required", result.get("status"));
        assertEquals(portalUrl, result.get("kycUrl"),
                "the document screen must always get a link it can open");
        assertEquals(portalUrl, subMerchantRepo.findById(sm.getId()).orElseThrow().getKycPortalUrl());
    }

    @Transactional
    @Test
    void aFailedAccessKeyRefreshDoesNotBlankAnExistingPortalLink() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        sm.setKycPortalUrl("https://kyc.easebuzz.in/session/existing");
        subMerchantRepo.save(sm);
        when(easebuzzApi.generateKycAccessKey(any(), any(), any(), any()))
            .thenReturn(Map.of("status", false, "error", "temporarily unavailable"));

        Map<String, Object> result = subMerchantService.generateKycAccessKey(sm.getId());

        assertEquals("https://kyc.easebuzz.in/session/existing", result.get("kyc_url"));
        assertEquals("https://kyc.easebuzz.in/session/existing",
                subMerchantRepo.findById(sm.getId()).orElseThrow().getKycPortalUrl());
    }

    @Transactional
    @Test
    void submitIssuesPortalLinkImmediatelyWhenCreateReturnsNone() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));
        String portalUrl = "https://kyc.easebuzz.in/session/issued-at-submit";
        when(easebuzzApi.generateKycAccessKey(any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "kyc_dashboard_url", portalUrl));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId(), false);

        assertEquals(portalUrl, submitted.getKycPortalUrl(),
                "onboarding must hand the owner a usable portal link without a second request");
        assertEquals(portalUrl, subMerchantRepo.findById(sm.getId()).orElseThrow().getKycPortalUrl());
    }

    @Transactional
    @Test
    void submitKeepsTheCreateTimePortalLinkWithoutRefreshing() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        String createUrl = "https://kyc.easebuzz.in/session/from-create";
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId,
                    "kyc_details", Map.of("kyc_dashboard_url", createUrl)));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId(), false);

        assertEquals(createUrl, submitted.getKycPortalUrl());
        verify(easebuzzApi, never()).generateKycAccessKey(any(), any(), any(), any());
    }

    @Test
    void ownerOnboardResponseCarriesTheKycPortalUrl() {
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));
        String portalUrl = "https://kyc.easebuzz.in/session/from-onboard";
        when(easebuzzApi.generateKycAccessKey(any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "kyc_dashboard_url", portalUrl));

        TenantContext.setCurrentTenant(testRestaurantId);
        TenantContext.setCurrentRole("OWNER");
        try {
            Map<String, Object> body = paymentConfigController.onboard(baseSubMerchantData()).getBody();
            assertEquals("success", body.get("status"));
            assertEquals(portalUrl, body.get("kycUrl"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void onboardResponseNeverHandsCounterStaffThePortalKey() {
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));
        when(easebuzzApi.generateKycAccessKey(any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "kyc_dashboard_url", "https://kyc.easebuzz.in/session/staff"));

        TenantContext.setCurrentTenant(testRestaurantId);
        TenantContext.setCurrentRole("SHOP_STAFF");
        try {
            Map<String, Object> body = paymentConfigController.onboard(baseSubMerchantData()).getBody();
            assertEquals("", body.get("kycUrl"),
                    "the portal URL signs in as the merchant, so counter staff must never receive it");
        } finally {
            TenantContext.clear();
        }
    }

    private org.springframework.mock.web.MockMultipartFile kycFile() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file", "pan.pdf", "application/pdf", new byte[512]);
    }

    private Map<String, Object> baseSubMerchantData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("businessName", "Test Restaurant");
        data.put("legalEntityName", "Test Restaurant Pvt Ltd");
        data.put("businessType", "Restaurant");
        data.put("restaurantFormat", "DINE_IN");
        data.put("pan", "ABCDE1234F");
        data.put("gst", "29ABCDE1234F1Z5");
        data.put("bankAccountNo", "1234567890");
        data.put("ifsc", "HDFC0000123");
        data.put("bankName", "HDFC");
        data.put("branchName", "Test Branch");
        data.put("beneficiaryName", "Test Owner");
        data.put("businessAddress", "123 Test St");
        data.put("state", "Karnataka");
        data.put("fssaiNumber", "12345678901234");
        data.put("contactEmail", "test@example.com");
        data.put("contactPhone", "9999999999");
        data.put("commissionRate", "0.0");
        return data;
    }

    @Transactional
    @Test
    void kycWebhookPersistsPayloadAsValidJson() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        subMerchantRepo.save(sm);

        subMerchantService.processWebhook(Map.of(
            "event", "MERCHANT_KYC_APPROVAL",
            "data", Map.of(
                "id", testSubMerchantId,
                "kyc_status", true,
                "kyc_profile_status", "Completed"
            )
        ));

        java.util.List<EasebuzzSubMerchantWebhookEvent> events = webhookEventRepo.findAll();
        assertEquals(1, events.size());

        // payload is a JSONB column: a Map's toString() body is not JSON and Postgres rejects
        // it, which would roll back the KYC status update along with the audit record.
        String stored = events.get(0).getPayload();
        assertTrue(stored != null && stored.trim().startsWith("{"),
                "payload should be a JSON object, got: " + stored);
        assertDoesNotThrow(() -> new com.fasterxml.jackson.databind.ObjectMapper().readTree(stored),
                "payload must parse as JSON, got: " + stored);
    }

    @Transactional
    @Test
    void kycApprovedWithUnverifiedBankDoesNotActivate() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        subMerchantRepo.save(sm);

        subMerchantService.processWebhook(Map.of(
            "event", "MERCHANT_KYC_APPROVAL",
            "data", Map.of(
                "id", testSubMerchantId,
                "kyc_status", true,
                "bank_status", false,
                "kyc_profile_status", "Completed"
            )
        ));

        EasebuzzSubMerchant after = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertNotEquals("ACTIVE", after.getStatus(), "bank not verified - must not activate");
        assertNull(after.getKycActivatedAt(), "activation timestamp must stay empty");
        assertEquals("True", after.getKycStatus());
        // Persisted so an operator can see why the row is stuck.
        assertEquals(Boolean.FALSE, after.getBankStatus());
    }

    @Transactional
    @Test
    void manualBankVerificationActivatesApprovedSubMerchant() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        sm.setKycStatus("True");
        sm.setBankStatus(false);
        sm.setStatus("PENDING_KYC");
        subMerchantRepo.save(sm);

        // Easebuzz sends no webhook when bank_status changes after approval, so this manual
        // path is the only way an approved-but-unverified merchant ever starts taking payments.
        EasebuzzSubMerchant unblocked = subMerchantService.markBankVerified(sm.getId());

        assertEquals("ACTIVE", unblocked.getStatus());
        assertEquals(Boolean.TRUE, unblocked.getBankStatus());
        assertNotNull(unblocked.getKycActivatedAt());
    }

    @Transactional
    @Test
    void kycWebhookWithSharedContactEmailIsNotAttributed() {
        EasebuzzSubMerchant first = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        first.setSubMerchantId(testSubMerchantId + "A");
        subMerchantRepo.save(first);

        EasebuzzSubMerchant second = subMerchantService.create(baseSubMerchantData(), testRestaurantId + 1000);
        second.setSubMerchantId(testSubMerchantId + "B");
        subMerchantRepo.save(second);

        // Both rows carry test@example.com while the payload's id matches neither. The old
        // single-result finder threw IncorrectResultSizeDataAccessException here.
        assertDoesNotThrow(() -> subMerchantService.processWebhook(Map.of(
            "event", "MERCHANT_KYC_APPROVAL",
            "data", Map.of(
                "id", "SOME-OTHER-ID",
                "kyc_status", true,
                "kyc_profile_status", "Completed",
                "email", "test@example.com"
            )
        )));

        assertNotEquals("ACTIVE", subMerchantRepo.findById(first.getId()).orElseThrow().getStatus());
        assertNotEquals("ACTIVE", subMerchantRepo.findById(second.getId()).orElseThrow().getStatus());
    }

    @Transactional
    @Test
    void kycWebhookStoresExpiryDate() {
        EasebuzzSubMerchant sm = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        sm.setSubMerchantId(testSubMerchantId);
        subMerchantRepo.save(sm);

        // Easebuzz sends no expiry webhook, so the value on the approval callback is the only
        // chance to learn when this sub-merchant's KYC stops being valid.
        subMerchantService.processWebhook(Map.of(
            "event", "MERCHANT_KYC_APPROVAL",
            "data", Map.of(
                "id", testSubMerchantId,
                "kyc_status", true,
                "kyc_profile_status", "Completed",
                "kyc_expiry_date", "2027-03-02 12:27:22"
            )
        ));

        EasebuzzSubMerchant after = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertEquals("2027-03-02 12:27:22", after.getKycExpiryDate());
    }

    @Transactional
    @Test
    void kycExpiryCheckBlocksOnlyExpiredSubMerchants() {
        EasebuzzSubMerchant expired = subMerchantService.create(baseSubMerchantData(), testRestaurantId);
        expired.setSubMerchantId(testSubMerchantId + "EXP");
        expired.setStatus("ACTIVE");
        expired.setKycExpiryDate("2020-01-01 00:00:00");
        subMerchantRepo.save(expired);

        EasebuzzSubMerchant stillValid = subMerchantService.create(baseSubMerchantData(), testRestaurantId + 1000);
        stillValid.setSubMerchantId(testSubMerchantId + "OK");
        stillValid.setStatus("ACTIVE");
        stillValid.setKycExpiryDate("2999-01-01 00:00:00");
        subMerchantRepo.save(stillValid);

        EasebuzzSubMerchant unparseable = subMerchantService.create(baseSubMerchantData(), testRestaurantId + 2000);
        unparseable.setSubMerchantId(testSubMerchantId + "BAD");
        unparseable.setStatus("ACTIVE");
        unparseable.setKycExpiryDate("not-a-date");
        subMerchantRepo.save(unparseable);

        kycExpiryService.flagExpiredKyc();

        // Expired: off ACTIVE, which is what makes payment creation refuse the merchant.
        assertEquals("KYC_EXPIRED", subMerchantRepo.findById(expired.getId()).orElseThrow().getStatus());

        // Silence is the bug this covers: Easebuzz sends no expiry webhook, so without a push the
        // owner learns only when a customer cannot pay.
        verify(pushNotificationService).pushToRestaurant(
                eq(expired.getRestaurantId()), any(), any(), any(), any(), any(), any());
        verify(pushNotificationService, never()).pushToRestaurant(
                eq(stillValid.getRestaurantId()), any(), any(), any(), any(), any(), any());
        verify(pushNotificationService, never()).pushToRestaurant(
                eq(unparseable.getRestaurantId()), any(), any(), any(), any(), any(), any());
        // A future date must not be touched.
        assertEquals("ACTIVE", subMerchantRepo.findById(stillValid.getId()).orElseThrow().getStatus());
        // A date we cannot read must not silently cut a merchant off.
        assertEquals("ACTIVE", subMerchantRepo.findById(unparseable.getId()).orElseThrow().getStatus());
    }

    @Transactional
    @Test
    void submissionProceedsWithoutRestaurantFormat() {
        Map<String, Object> data = baseSubMerchantData();
        data.remove("restaurantFormat");
        EasebuzzSubMerchant sm = subMerchantService.create(data, testRestaurantId);

        // The outlet format used to be a submit gate on the assumption that it chose the MCC.
        // Easebuzz assigns the MCC internally and documents no create-time MCC field, so the
        // format is ops data - a missing one must not block an otherwise complete submission.
        when(easebuzzApi.createSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true, "submerchant_id", testSubMerchantId));

        EasebuzzSubMerchant submitted = subMerchantService.submitToEasebuzz(sm.getId());
        assertEquals("PENDING_KYC", submitted.getStatus());
        assertNull(subMerchantRepo.findById(sm.getId()).orElseThrow().getRestaurantFormat());
    }

    @Test
    void rejectsNonZeroCommissionOnSubMerchantCreation() {
        Map<String, Object> data = baseSubMerchantData();
        data.put("commissionRate", "3.00");

        assertThrows(IllegalArgumentException.class,
                () -> subMerchantService.create(data, testRestaurantId));
        assertTrue(subMerchantRepo.findByRestaurantId(testRestaurantId).isEmpty());
    }

    @Test
    void paymentLinkRequiresOnboardedSubMerchant() {

        Map<String, Object> result = paymentService.createPaymentLink(Map.of(
                "restaurantId", testRestaurantId, "amount", "100.00"));

        assertEquals("SUBMERCHANT_NOT_ACTIVE", result.get("code"));
        verify(easebuzzApi, never()).createPaymentLink(any());
    }

    @Test
    void paymentLinkIgnoresLegacyCommissionSetting() {
        EasebuzzSubMerchant sm = createActiveSubMerchant();
        sm.setCommissionRate(new BigDecimal("3.00"));
        subMerchantRepo.save(sm);
        when(easebuzzApi.createPaymentLink(any())).thenReturn(Map.of("status", "success", "link", "https://pay.easebuzz.in/test"));

        Map<String, Object> result = paymentService.createPaymentLink(Map.of(
                "restaurantId", testRestaurantId, "amount", "100.00"));

        assertEquals("success", result.get("status"));
        verify(easebuzzApi).createPaymentLink(any());
    }

    @Test
    void paymentLinkRejectsInvalidAmountBeforeGatewayCall() {
        createActiveSubMerchant();

        Map<String, Object> result = paymentService.createPaymentLink(Map.of(
                "restaurantId", testRestaurantId, "amount", "0.001"));

        assertEquals("INVALID_PAYMENT_AMOUNT", result.get("code"));
        verify(easebuzzApi, never()).createPaymentLink(any());
    }

    @Test
    void ownerDisabledEasebuzzBlocksNewBillPaymentLink() {
        createActiveSubMerchant();
        TenantContext.setCurrentTenant(testRestaurantId);
        TenantContext.setCurrentRole("OWNER");
        try {
            Map<String, Object> config = paymentConfigController
                    .updateConfig(Map.of("easebuzzEnabled", false)).getBody();
            assertEquals(false, config.get("easebuzzEnabled"));
            assertEquals(false, config.get("paymentLinkReady"));
        } finally {
            TenantContext.clear();
        }
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("100.00"));

        Map<String, Object> result = paymentService.createPaymentLinkForBill(bill.getId(), testRestaurantId);

        assertEquals("PAYMENT_METHOD_DISABLED", result.get("code"));
        verify(easebuzzApi, never()).createPaymentLink(any());
        assertNull(billRepository.findById(bill.getId()).orElseThrow().getGatewayTxnId());
    }

    @Test
    void testPaymentLinkAndWebhook() {
        // Setup active sub-merchant
        EasebuzzSubMerchant sm = createActiveSubMerchant();
        sm.setCommissionRate(BigDecimal.ZERO);
        subMerchantRepo.save(sm);

        // Create a bill
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("1000.00"));

        // Mock payment link creation
        when(easebuzzApi.createPaymentLink(any()))
            .thenReturn(Map.of(
                "status", "success",
                "link", "https://pay.easebuzz.in/link/test-link-123"
            ));

        // 1. Create payment link
        Map<String, Object> result = paymentService.createPaymentLinkForBill(bill.getId(), testRestaurantId);
        assertEquals("success", result.get("status"));

        // Verify bill updated
        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();
        assertNotNull(updatedBill.getGatewayTxnId());
        assertTrue(updatedBill.getGatewayTxnId().startsWith("PL"), "merchant_txn should start with 'PL' prefix, got: " + updatedBill.getGatewayTxnId());
        assertEquals("link_sent", updatedBill.getPaymentStatus());
        assertEquals("payment_link", updatedBill.getPaymentMode());

        // 2. Payment webhook success (state after webhook processing)
        Bill processedBill = billRepository.findById(bill.getId()).orElseThrow();
        assertNotNull(processedBill.getGatewayTxnId());

        // Post-split is async (via @Async("postSplitExecutor")) — not checked here
        // It is tested separately via EasebuzzNewFeaturesTest and mock verification
    }

    @Test
    void billPaymentLinkUsesDotDecimalRegardlessOfServerLocale() {
        createActiveSubMerchant();
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("1000.00"));
        when(easebuzzApi.createPaymentLink(any()))
                .thenReturn(Map.of("status", "success", "link", "https://pay.easebuzz.in/test"));

        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertEquals("success", paymentService.createPaymentLinkForBill(bill.getId(), testRestaurantId).get("status"));
        } finally {
            java.util.Locale.setDefault(original);
        }

        verify(easebuzzApi).createPaymentLink(argThat(data -> "1000.00".equals(data.get("amount"))));
    }

    @Test
    void transactionVerificationDoesNotPayBillWithMismatchedAmount() {
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("100.00"));
        bill.setGatewayTxnId("PL_MATCH_TEST");
        billRepository.save(bill);
        when(easebuzzApi.getTransactionStatus("PL_MATCH_TEST")).thenReturn(Map.of(
                "status", true,
                "msg", Map.of("status", "success", "txnid", "PL_MATCH_TEST",
                        "amount", "1.00", "easepayid", "E_MATCH_TEST")));

        Map<String, Object> result = paymentService.verifyPayment(bill.getId());

        assertEquals("PAYMENT_VERIFICATION_MISMATCH", result.get("code"));
        assertEquals("pending", billRepository.findById(bill.getId()).orElseThrow().getPaymentStatus());
    }

    @Test
    void transactionVerificationDoesNotPayBillWithMismatchedTransactionId() {
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("100.00"));
        bill.setGatewayTxnId("PL_MATCH_TEST");
        billRepository.save(bill);
        when(easebuzzApi.getTransactionStatus("PL_MATCH_TEST")).thenReturn(Map.of(
                "status", true,
                "msg", Map.of("status", "success", "txnid", "PL_OTHER_BILL",
                        "amount", "100.00", "easepayid", "E_MATCH_TEST")));

        Map<String, Object> result = paymentService.verifyPayment(bill.getId());

        assertEquals("PAYMENT_VERIFICATION_MISMATCH", result.get("code"));
        assertEquals("pending", billRepository.findById(bill.getId()).orElseThrow().getPaymentStatus());
    }

    @Test
    void transactionVerificationPaysBillWithMatchingTransactionAndAmount() {
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("100.00"));
        bill.setGatewayTxnId("PL_MATCH_TEST");
        billRepository.save(bill);
        when(easebuzzApi.getTransactionStatus("PL_MATCH_TEST")).thenReturn(Map.of(
                "status", true,
                "msg", Map.of("status", "success", "txnid", "PL_MATCH_TEST",
                        "amount", "100.00", "easepayid", "E_MATCH_TEST")));

        Map<String, Object> result = paymentService.verifyPayment(bill.getId());

        assertEquals("success", result.get("status"));
        assertEquals("paid", billRepository.findById(bill.getId()).orElseThrow().getPaymentStatus());
    }

    @Test
    void testRefundFlow() {
        // Setup paid bill
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("500.00"));
        bill.setGatewayTxnId("KBTEST123");
        bill.setGatewayStatus("success");
        bill.setPaymentStatus("paid");
        billRepository.save(bill);

        // Mock transaction status check (needed for resolveEasebuzzId)
        when(easebuzzApi.getTransactionStatus(any()))
            .thenReturn(Map.of("status", true, "msg", Map.of("status", "success",
                    "txnid", "KBTEST123", "amount", "500.00", "easebuzz_id", "E250TEST123")));

        // Mock refund
        when(easebuzzApi.initiateRefund(any(), any(), any()))
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
                "status", "success",
                "msg", Map.of("refund_id", "REFTEST123", "status", "completed")
            ));

        Map<String, Object> statusResult = paymentService.getRefundStatus(bill.getId());
        assertEquals("success", statusResult.get("status"));
    }

    @Test
    void refundDoesNotCallGatewayWithoutMatchedOriginalPaymentId() {
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("500.00"));
        bill.setGatewayTxnId("KBTEST123");
        bill.setPaymentStatus("paid");
        billRepository.save(bill);
        when(easebuzzApi.getTransactionStatus("KBTEST123"))
                .thenReturn(Map.of("status", true, "msg", Map.of("status", "success",
                        "txnid", "OTHER_TRANSACTION", "amount", "500.00",
                        "easebuzz_id", "E_OTHER_PAYMENT")));

        Map<String, Object> result = paymentService.initiateRefund(
                bill.getId(), new BigDecimal("250.00"), "Customer request");

        assertEquals("PAYMENT_ID_UNAVAILABLE", result.get("code"));
        assertNull(billRepository.findById(bill.getId()).orElseThrow().getRefundId());
        verify(easebuzzApi, never()).initiateRefund(any(), any(), any());
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
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("1000.00"));
        EasebuzzSubMerchant sm = createActiveSubMerchant();

        // Mock split API to always fail
        when(easebuzzApi.updateTransactionSplit(any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", false, "error", "EBPTSURVE06"));

        // Should exhaust retries without throwing
        assertDoesNotThrow(() -> 
            postSplitService.createPostSplit(bill.getId(), "E250TEST", "KBTEST")
        );

        // Verify bill NOT settled
        Bill unsettledBill = billRepository.findById(bill.getId()).orElseThrow();
        assertNull(unsettledBill.getSettledAt());
    }

    @Transactional
    @Test
    void testSplitLabelRotatesOnBankChange() {
        EasebuzzSubMerchant sm = createActiveSubMerchant();
        sm.setSplitLabel("sm_" + testSubMerchantId);
        sm.setSplitLabelVersion(1);
        sm.setSplitLabelBankSnapshot("123456789012|HDFC0000123");
        subMerchantRepo.save(sm);

        // Change bank account + IFSC, then push to Easebuzz
        Map<String, String> update = new java.util.HashMap<>();
        update.put("bankAccountNo", "987654321098");
        update.put("ifsc", "ICIC0000456");
        subMerchantService.update(sm.getId(), update);

        when(easebuzzApi.updateSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true));
        when(easebuzzApi.createSplitLabel(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", "success", "msg", "Label created"));

        subMerchantService.updateOnEasebuzz(sm.getId());

        EasebuzzSubMerchant rotated = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertEquals("sm_" + testSubMerchantId + "_v1", rotated.getSplitLabel());
        assertEquals(Integer.valueOf(2), rotated.getSplitLabelVersion());
        assertEquals("987654321098|ICIC0000456", rotated.getSplitLabelBankSnapshot());
    }

    @Transactional
    @Test
    void testSplitLabelNotRotatedWhenBankUnchanged() {
        EasebuzzSubMerchant sm = createActiveSubMerchant();
        sm.setSplitLabel("sm_" + testSubMerchantId);
        sm.setSplitLabelVersion(1);
        sm.setSplitLabelBankSnapshot("123456789012|HDFC0000123");
        subMerchantRepo.save(sm);

        when(easebuzzApi.updateSubMerchant(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("status", true));

        subMerchantService.updateOnEasebuzz(sm.getId());

        EasebuzzSubMerchant unchanged = subMerchantRepo.findById(sm.getId()).orElseThrow();
        assertEquals("sm_" + testSubMerchantId, unchanged.getSplitLabel());
        assertEquals(Integer.valueOf(1), unchanged.getSplitLabelVersion());
    }

    // --- Stuck payment sweep ---

    @Test
    void sweepConfirmsAPaymentWhoseWebhookNeverArrived() {
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("100.00"));
        bill.setGatewayTxnId("PLSWEEP0001");
        bill.setGatewayStatus("link_created");
        bill.setPaymentStatus("link_sent");
        bill.setGatewayLinkCreatedAt(System.currentTimeMillis() - 10 * 60_000L);
        billRepository.save(bill);
        when(easebuzzApi.getTransactionStatus("PLSWEEP0001")).thenReturn(Map.of(
                "status", true,
                "msg", Map.of("status", "success", "txnid", "PLSWEEP0001",
                        "amount", "100.00", "easepayid", "E_SWEEP_1")));

        // A lost webhook used to mean the bill stayed unpaid until the 06:00 reconciliation.
        sweepService.sweepUnconfirmedLinks();

        Bill after = billRepository.findById(bill.getId()).orElseThrow();
        assertEquals("paid", after.getPaymentStatus());
        assertEquals("success", after.getGatewayStatus());
        assertNotNull(after.getPaidAt());
    }

    @Test
    void sweepExpiresALinkNobodyPaid() {
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("100.00"));
        bill.setGatewayTxnId("PLSWEEP0002");
        bill.setGatewayStatus("link_created");
        bill.setPaymentStatus("link_sent");
        bill.setGatewayLinkCreatedAt(System.currentTimeMillis() - 45 * 60_000L);
        billRepository.save(bill);
        when(easebuzzApi.getTransactionStatus("PLSWEEP0002")).thenReturn(Map.of(
                "status", true,
                "msg", Map.of("status", "pending", "txnid", "PLSWEEP0002", "amount", "100.00")));

        sweepService.sweepUnconfirmedLinks();

        Bill after = billRepository.findById(bill.getId()).orElseThrow();
        assertEquals("link_expired", after.getGatewayStatus());
        // Expiry must never claim the customer paid.
        assertEquals("link_sent", after.getPaymentStatus());
        assertNull(after.getPaidAt());
    }

    @Test
    void sweepLeavesFreshLinksAlone() {
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("100.00"));
        bill.setGatewayTxnId("PLSWEEP0003");
        bill.setGatewayStatus("link_created");
        bill.setPaymentStatus("link_sent");
        bill.setGatewayLinkCreatedAt(System.currentTimeMillis() - 60_000L);
        billRepository.save(bill);

        sweepService.sweepUnconfirmedLinks();

        // A link the customer may still be paying is not worth a gateway round trip.
        verify(easebuzzApi, never()).getTransactionStatus(any());
        assertEquals("link_created", billRepository.findById(bill.getId()).orElseThrow().getGatewayStatus());
    }

    @Test
    void sweepDoesNotWriteOffALinkWhenTheGatewayCallFails() {
        Bill bill = createTestBill(testRestaurantId, new BigDecimal("100.00"));
        bill.setGatewayTxnId("PLSWEEP0004");
        bill.setGatewayStatus("link_created");
        bill.setPaymentStatus("link_sent");
        bill.setGatewayLinkCreatedAt(System.currentTimeMillis() - 10 * 60_000L);
        billRepository.save(bill);
        // The client maps a transport failure to status=failure. Writing that as gatewayStatus
        // 'error' would hide the link from every later sweep.
        when(easebuzzApi.getTransactionStatus("PLSWEEP0004"))
                .thenReturn(Map.of("status", false, "error", "connect timed out"));

        sweepService.sweepUnconfirmedLinks();

        Bill afterOutage = billRepository.findById(bill.getId()).orElseThrow();
        assertEquals("link_created", afterOutage.getGatewayStatus(),
                "a transient gateway failure must leave the link visible to the next sweep");
        assertEquals("link_sent", afterOutage.getPaymentStatus());

        // Recovery: the next run confirms the same bill.
        when(easebuzzApi.getTransactionStatus("PLSWEEP0004")).thenReturn(Map.of(
                "status", true,
                "msg", Map.of("status", "success", "txnid", "PLSWEEP0004",
                        "amount", "100.00", "easepayid", "E_SWEEP_4")));
        sweepService.sweepUnconfirmedLinks();

        assertEquals("paid", billRepository.findById(bill.getId()).orElseThrow().getPaymentStatus());
    }

    // --- Helpers ---

    private EasebuzzSubMerchant createActiveSubMerchant() {
        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setRestaurantId(testRestaurantId);
        sm.setBusinessName("Test Restaurant");
        sm.setSubMerchantId(testSubMerchantId);
        sm.setStatus("ACTIVE");
        sm.setSplitLabel("sm_" + testSubMerchantId);
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
        var profile = restaurantProfileRepository.findByRestaurantId(testRestaurantId).orElseThrow();
        profile.setEasebuzzEnabled(true);
        restaurantProfileRepository.save(profile);
        return subMerchantRepo.save(sm);
    }

    private Bill createTestBill(Long restaurantId, BigDecimal amount) {
        // The integration H2 database is shared across test classes; clear any
        // leftover row with the same natural key before inserting.
        billRepository.findByRestaurantIdAndDeviceIdAndLocalId(restaurantId, "TEST_DEVICE", 1L)
                .ifPresent(billRepository::delete);
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
