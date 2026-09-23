package com.khanabook.saas.service;

import com.khanabook.saas.feature.payments.service.EasebuzzWireApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.feature.payments.service.EasebuzzWebhookService;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import com.khanabook.saas.feature.payments.service.EasebuzzProperties;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzPayoutRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.RefundAttempt;
import com.khanabook.saas.feature.payments.data.RefundAttemptRepository;
import com.khanabook.saas.feature.compliance.data.FssaiRenewalRepository;
import com.khanabook.saas.feature.compliance.data.FssaiTrackerRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.compliance.data.FssaiRenewal;
import com.khanabook.saas.feature.compliance.data.FssaiTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EasebuzzWebhookTest {

    @Mock private BillRepository billRepo;
    @Mock private EasebuzzWebhookEventRepository webhookEventRepo;
    @Mock private RefundAttemptRepository refundAttemptRepo;
    @Mock private EasebuzzProperties props;
    @Mock private com.khanabook.saas.feature.payments.service.WebhookRetryService webhookRetryService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private SubMerchantService subMerchantService;
    @Mock private EasebuzzPayoutRepository payoutRepo;
    @Mock private PushNotificationService pushNotificationService;
    @Mock private FssaiRenewalRepository fssaiRenewalRepo;
    @Mock private FssaiTrackerRepository fssaiTrackerRepo;
    @Mock private EasebuzzSubMerchantRepository subMerchantRepo;
    @Mock private org.springframework.core.env.Environment env;
    @Mock private EasebuzzWireApiClient wireApiClient;

    @InjectMocks
    private EasebuzzWebhookService webhookService;

    private final String TEST_KEY = "CHANGE_ME_SANDBOX_KEY";
    private final String TEST_SALT = "CHANGE_ME_SANDBOX_SALT";

    @BeforeEach
    void setup() {
        lenient().when(props.getMerchantKey()).thenReturn(TEST_KEY);
        lenient().when(props.getSalt()).thenReturn(TEST_SALT);
        lenient().when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});
    }

    @Test
    void testHandlePaymentWebhookSuccess() throws Exception {
        // 1. Prepare payload
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", "KB12345");
        payload.put("status", "success");
        payload.put("amount", "100.00");
        payload.put("udf1", "500"); // billId
        payload.put("easepayid", "E250TEST");
        payload.put("firstname", "John");
        payload.put("email", "john@example.com");
        payload.put("productinfo", "Test Product");
        
        // Generate valid reverse hash
        String hash = generateReverseHash(payload);
        payload.put("hash", hash);

        // 2. Mock behavior
        Bill mockBill = new Bill();
        mockBill.setId(500L);
        mockBill.setRestaurantId(1L);
        mockBill.setGatewayTxnId("KB12345");
        mockBill.setTotalAmount(new BigDecimal("100.00"));
        when(billRepo.findByGatewayTxnId("KB12345")).thenReturn(Optional.of(mockBill));

        // 3. Execute
        Map<String, Object> response = webhookService.handlePaymentWebhook(payload);

        // 4. Verify
        assertEquals("received", response.get("status"));
        assertEquals("paid", mockBill.getPaymentStatus());
        assertEquals("success", mockBill.getGatewayStatus());
        assertEquals("KB12345", mockBill.getGatewayTxnId());
        
        verify(webhookRetryService).enqueueAt(eq("POST_SPLIT"), contains("\"billId\":500"),
                anyLong(), eq("POST_SPLIT:500:E250TEST"));
    }

    @Test
    void testHandlePaymentWebhookSuccessWithTxnidFallback() throws Exception {
        // Prepare payload with missing udf1 but known txnid
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", "PL12345678");
        payload.put("status", "success");
        payload.put("amount", "250.00");
        payload.put("easepayid", "E250TEST2");
        payload.put("firstname", "Alice");
        payload.put("email", "alice@example.com");
        payload.put("productinfo", "Order Payment");
        
        String hash = generateReverseHash(payload);
        payload.put("hash", hash);

        Bill mockBill = new Bill();
        mockBill.setId(777L);
        mockBill.setRestaurantId(1L);
        mockBill.setGatewayTxnId("PL12345678");
        mockBill.setTotalAmount(new BigDecimal("250.00"));
        when(billRepo.findByGatewayTxnId("PL12345678")).thenReturn(Optional.of(mockBill));

        Map<String, Object> response = webhookService.handlePaymentWebhook(payload);

        assertEquals("received", response.get("status"));
        assertEquals("paid", mockBill.getPaymentStatus());
        assertEquals("success", mockBill.getGatewayStatus());
        verify(webhookRetryService).enqueueAt(eq("POST_SPLIT"), contains("\"billId\":777"),
                anyLong(), eq("POST_SPLIT:777:E250TEST2"));
    }

    @Test
    void testHandlePaymentWebhookHashMismatch() {
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", "KB12345");
        payload.put("hash", "wrong_hash");

        Map<String, Object> response = webhookService.handlePaymentWebhook(payload);

        assertEquals("hash_mismatch", response.get("status"));
        verifyNoInteractions(billRepo);
    }

    @Test
    void signedWebhookCannotPayBillUsingUnissuedTransaction() throws Exception {
        Map<String, String> payload = signedPaymentPayload("OTHER_TXN", "500", "100.00", "1");
        when(billRepo.findByGatewayTxnId("OTHER_TXN")).thenReturn(Optional.empty());

        webhookService.handlePaymentWebhook(payload);

        verify(billRepo, never()).save(any());
        verify(webhookRetryService, never()).enqueueAt(eq("POST_SPLIT"), anyString(), anyLong(), anyString());
    }

    @Test
    void signedWebhookCannotPayDifferentBillOrAmount() throws Exception {
        Bill bill = new Bill();
        bill.setId(500L);
        bill.setRestaurantId(1L);
        bill.setGatewayTxnId("PL_500");
        bill.setPaymentStatus("link_sent");
        bill.setTotalAmount(new BigDecimal("100.00"));
        when(billRepo.findByGatewayTxnId("PL_500")).thenReturn(Optional.of(bill));

        webhookService.handlePaymentWebhook(signedPaymentPayload("PL_500", "501", "100.00", "1"));
        webhookService.handlePaymentWebhook(signedPaymentPayload("PL_500", "500", "1.00", "1"));
        webhookService.handlePaymentWebhook(signedPaymentPayload("PL_500", "500", "100.00", "2"));

        assertEquals("link_sent", bill.getPaymentStatus());
        verify(billRepo, never()).save(any());
        verify(webhookRetryService, never()).enqueueAt(eq("POST_SPLIT"), anyString(), anyLong(), anyString());
    }

    private Map<String, String> signedPaymentPayload(String txnid, String billId, String amount, String restaurantId) throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", txnid);
        payload.put("status", "success");
        payload.put("amount", amount);
        payload.put("udf1", billId);
        payload.put("udf2", restaurantId);
        payload.put("easepayid", "E_VALID_TEST");
        payload.put("firstname", "Customer");
        payload.put("email", "customer@example.com");
        payload.put("productinfo", "Order");
        payload.put("hash", generateReverseHash(payload));
        return payload;
    }

    @Test
    void completedPartialRefundKeepsBillPartiallyRefunded() throws Exception {
        Bill bill = refundBill(new BigDecimal("100.00"), BigDecimal.ZERO);
        Map<String, String> payload = refundPayload("30.00");
        when(billRepo.findByGatewayTxnId("PL_REFUND_TEST")).thenReturn(Optional.of(bill));
        stubOriginalPaymentEvent(bill, "E_REFUND_TEST");
        stubRefundAttempt(bill, new BigDecimal("30.00"));

        assertEquals("received", webhookService.handleRefundWebhook(payload).get("status"));

        assertEquals("partially_refunded", bill.getPaymentStatus());
        assertEquals("partially_refunded", bill.getGatewayStatus());
        assertEquals(new BigDecimal("30.00"), bill.getRefundAmount());
        verify(billRepo).save(bill);
    }

    @Test
    void completedFullRefundMarksBillRefunded() throws Exception {
        Bill bill = refundBill(new BigDecimal("100.00"), BigDecimal.ZERO);
        when(billRepo.findByGatewayTxnId("PL_REFUND_TEST")).thenReturn(Optional.of(bill));
        stubOriginalPaymentEvent(bill, "E_REFUND_TEST");
        stubRefundAttempt(bill, new BigDecimal("100.00"));

        webhookService.handleRefundWebhook(refundPayload("100.00"));

        assertEquals("refunded", bill.getPaymentStatus());
        assertEquals("refunded", bill.getGatewayStatus());
    }

    @Test
    void duplicateRefundCompletionDoesNotDoubleCount() throws Exception {
        Bill bill = refundBill(new BigDecimal("100.00"), BigDecimal.ZERO);
        when(billRepo.findByGatewayTxnId("PL_REFUND_TEST")).thenReturn(Optional.of(bill));
        stubOriginalPaymentEvent(bill, "E_REFUND_TEST");
        RefundAttempt attempt = stubRefundAttempt(bill, new BigDecimal("30.00"));

        webhookService.handleRefundWebhook(refundPayload("30.00"));
        webhookService.handleRefundWebhook(refundPayload("30.00"));

        assertEquals(new BigDecimal("30.00"), bill.getRefundAmount());
        assertEquals("COMPLETED", attempt.getStatus());
        verify(billRepo, times(1)).save(bill);
    }

    @Test
    void refundCompletionAmountMustMatchRecordedAttempt() throws Exception {
        Bill bill = refundBill(new BigDecimal("100.00"), BigDecimal.ZERO);
        when(billRepo.findByGatewayTxnId("PL_REFUND_TEST")).thenReturn(Optional.of(bill));
        stubOriginalPaymentEvent(bill, "E_REFUND_TEST");
        RefundAttempt attempt = stubRefundAttempt(bill, new BigDecimal("50.00"));

        webhookService.handleRefundWebhook(refundPayload("30.00"));

        assertEquals(BigDecimal.ZERO, bill.getRefundAmount());
        assertEquals("paid", bill.getPaymentStatus());
        assertEquals("INITIATED", attempt.getStatus());
        assertEquals("refund_review_required", bill.getGatewayStatus());
    }

    @Test
    void unconfirmedFailureCallbackKeepsAttemptReservedForReview() throws Exception {
        Bill bill = refundBill(new BigDecimal("100.00"), BigDecimal.ZERO);
        when(billRepo.findByGatewayTxnId("PL_REFUND_TEST")).thenReturn(Optional.of(bill));
        stubOriginalPaymentEvent(bill, "E_REFUND_TEST");
        RefundAttempt attempt = stubRefundAttempt(bill, new BigDecimal("30.00"));
        Map<String, String> payload = refundPayload("30.00");
        payload.put("status", "failed");

        webhookService.handleRefundWebhook(payload);

        assertEquals("INITIATED", attempt.getStatus());
        assertEquals(BigDecimal.ZERO, bill.getRefundAmount());
        assertEquals("refund_review_required", bill.getGatewayStatus());
        verify(refundAttemptRepo, never()).save(attempt);
    }

    @Test
    void completedRefundWithoutUsableAmountNeedsReview() throws Exception {
        Bill bill = refundBill(new BigDecimal("100.00"), BigDecimal.ZERO);
        when(billRepo.findByGatewayTxnId("PL_REFUND_TEST")).thenReturn(Optional.of(bill));
        stubOriginalPaymentEvent(bill, "E_REFUND_TEST");
        stubRefundAttempt(bill, new BigDecimal("30.00"));

        webhookService.handleRefundWebhook(refundPayload("not-an-amount"));

        assertEquals("paid", bill.getPaymentStatus());
        assertEquals("refund_review_required", bill.getGatewayStatus());
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void refundWebhookCannotUseValidHashForDifferentPayment() throws Exception {
        Bill bill = refundBill(new BigDecimal("100.00"), new BigDecimal("30.00"));
        when(billRepo.findByGatewayTxnId("PL_REFUND_TEST")).thenReturn(Optional.of(bill));
        stubOriginalPaymentEvent(bill, "E_DIFFERENT_PAYMENT");

        assertEquals("received", webhookService.handleRefundWebhook(refundPayload("30.00")).get("status"));

        assertEquals("paid", bill.getPaymentStatus());
        verify(billRepo, never()).save(any());
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void refundWebhookWithoutOriginalPaymentIdNeedsReconciliation() throws Exception {
        Bill bill = refundBill(new BigDecimal("100.00"), new BigDecimal("30.00"));
        when(billRepo.findByGatewayTxnId("PL_REFUND_TEST")).thenReturn(Optional.of(bill));

        webhookService.handleRefundWebhook(refundPayload("30.00"));

        assertEquals("paid", bill.getPaymentStatus());
        verify(billRepo, never()).save(any());
    }

    private void stubOriginalPaymentEvent(Bill bill, String easebuzzId) {
        EasebuzzWebhookEvent event = new EasebuzzWebhookEvent();
        event.setEasebuzzId(easebuzzId);
        when(webhookEventRepo.findByRestaurantIdAndTxnId(bill.getRestaurantId(), bill.getGatewayTxnId()))
                .thenReturn(Optional.of(event));
    }

    private RefundAttempt stubRefundAttempt(Bill bill, BigDecimal amount) {
        RefundAttempt attempt = new RefundAttempt();
        attempt.setBillId(bill.getId());
        attempt.setGatewayTxnId(bill.getGatewayTxnId());
        attempt.setEasebuzzPaymentId("E_REFUND_TEST");
        attempt.setGatewayRefundId("REF_TEST");
        attempt.setAmount(amount);
        attempt.setStatus("INITIATED");
        when(refundAttemptRepo.findByBillIdAndGatewayRefundId(bill.getId(), "REF_TEST"))
                .thenReturn(Optional.of(attempt));
        return attempt;
    }

    private Bill refundBill(BigDecimal total, BigDecimal refunded) {
        Bill bill = new Bill();
        bill.setId(500L);
        bill.setRestaurantId(1L);
        bill.setGatewayTxnId("PL_REFUND_TEST");
        bill.setPaymentStatus("paid");
        bill.setTotalAmount(total);
        bill.setRefundAmount(refunded);
        return bill;
    }

    private Map<String, String> refundPayload(String amount) throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", "PL_REFUND_TEST");
        payload.put("status", "refunded");
        payload.put("easepayid", "E_REFUND_TEST");
        payload.put("refund_id", "REF_TEST");
        payload.put("refund_amount", amount);
        payload.put("hash", sha512(TEST_KEY + "|E_REFUND_TEST|" + TEST_SALT));
        return payload;
    }

    @Test
    void testHandlePayoutWebhookSuccess() throws Exception {
        // 1. Prepare payload
        Map<String, String> payload = new HashMap<>();
        payload.put("payout_id", "PO_12345");
        payload.put("status", "paid_out");
        
        // Hash: key|payout_id|salt
        String hashStr = TEST_KEY + "|" + "PO_12345" + "|" + TEST_SALT;
        payload.put("hash", sha512(hashStr));

        // 2. Execute
        Map<String, Object> response = webhookService.handlePayoutWebhook(payload);

        // 3. Verify
        assertEquals("received", response.get("status"));
    }

    @Test
    void testHandleEraSettlementPayoutWebhookWithNestedData() throws Exception {
        // Era's official format with status: 1 and nested "data" object
        Map<String, Object> data = new HashMap<>();
        data.put("payout_id", "PT1LCHKBAB");
        data.put("bank_transaction_id", "YESBN5202501300640000000");
        data.put("payout_amount", 2.0);
        data.put("settled_transactions", java.util.Collections.emptyList());
        
        // Reverse Hash Formula: sha512(key|payout_id|salt)
        String hashStr = TEST_KEY + "|PT1LCHKBAB|" + TEST_SALT;
        data.put("hash", sha512(hashStr));

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", 1);
        payload.put("data", data);

        Map<String, Object> response = webhookService.handlePayoutWebhook(payload);

        assertEquals("received", response.get("status"));
    }

    @Test
    void testHandleTransferPayoutWebhookSuccess() throws Exception {
        // 2. Scenario 2: Transfer (Payout V2) Webhook 
        // Hash: key|beneficiary_account_number|ifsc|beneficiary_upi_handle|unique_request_number|amount|unique_transaction_reference|status|salt
        Map<String, String> payload = new HashMap<>();
        payload.put("beneficiary_account_number", "123456789");
        payload.put("ifsc", "HDFC0001");
        payload.put("unique_request_number", "REQ_001");
        payload.put("amount", "500.00");
        payload.put("status", "success");
        
        StringBuilder sb = new StringBuilder();
        sb.append(TEST_KEY).append("|");
        sb.append("123456789").append("|");
        sb.append("HDFC0001").append("|");
        sb.append("").append("|"); // upi
        sb.append("REQ_001").append("|");
        sb.append("500.00").append("|");
        sb.append("").append("|"); // utr
        sb.append("success").append("|");
        sb.append(TEST_SALT);
        
        payload.put("hash", sha512(sb.toString()));

        Map<String, Object> response = webhookService.handlePayoutWebhook(payload);

        assertEquals("received", response.get("status"));
    }

    @Test
    void testHandleFssaiRenewalWebhookSuccess() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", "KBF9501TEST");
        payload.put("status", "success");
        payload.put("amount", "2000.00");
        payload.put("udf1", "fssai_renewal");
        payload.put("udf2", "9501");
        payload.put("udf3", "13023001000255");
        payload.put("udf4", "2");

        String hash = generateReverseHash(payload);
        payload.put("hash", hash);

        FssaiRenewal renewal = new FssaiRenewal();
        renewal.setRestaurantId(9501L);
        renewal.setFssaiNumber("13023001000255");
        renewal.setYears(2);
        renewal.setEasebuzzTxnId("KBF9501TEST");
        renewal.setStatus("PENDING");

        when(fssaiRenewalRepo.findByEasebuzzTxnId("KBF9501TEST")).thenReturn(Optional.of(renewal));
        
        FssaiTracker tracker = new FssaiTracker();
        tracker.setRestaurantId(9501L);
        when(fssaiTrackerRepo.findByRestaurantId(9501L)).thenReturn(Optional.of(tracker));

        Map<String, Object> response = webhookService.handlePaymentWebhook(payload);

        assertEquals("received", response.get("status"));
        assertEquals("SUCCESS", renewal.getStatus());
        assertEquals("RENEWAL_PAID", tracker.getStatus());
        
        verify(pushNotificationService, times(1)).pushToRestaurant(
                eq(9501L),
                eq("FSSAI Renewal Paid"),
                anyString(),
                eq("system"),
                eq("13023001000255"),
                eq("fssai"),
                eq(new java.math.BigDecimal("2000.00"))
        );
    }

    @Test
    void testUnhashedMerchantKycApproval_SubMerchantNotFound_Rejected() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "UNKNOWN_SM_ID");
        data.put("email", "unknown@restaurant.com");

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "MERCHANT_KYC_APPROVAL");
        payload.put("data", data);

        when(subMerchantRepo.findBySubMerchantId("UNKNOWN_SM_ID")).thenReturn(Optional.empty());
        when(subMerchantRepo.findByContactEmail("unknown@restaurant.com")).thenReturn(Optional.empty());

        Map<String, Object> result = webhookService.handleSubMerchantWebhook(payload);
        assertEquals("hash_mismatch", result.get("status"));
        verify(subMerchantService, never()).processWebhook(any());
    }

    @Test
    void testUnhashedMerchantKycApproval_SubMerchantFound_Accepted() {
        Map<String, Object> data = new HashMap<>();
        data.put("submerchant_id", "KNOWN_SM_ID");
        data.put("email", "known@restaurant.com");

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "MERCHANT_KYC_APPROVAL");
        payload.put("data", data);

        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setSubMerchantId("KNOWN_SM_ID");
        when(subMerchantRepo.findBySubMerchantId("KNOWN_SM_ID")).thenReturn(Optional.of(sm));

        Map<String, Object> result = webhookService.handleSubMerchantWebhook(payload);
        assertEquals("received", result.get("status"));
        verify(subMerchantService, times(1)).processWebhook(payload);
    }

    @Test
    void testUnhashedMerchantKycApproval_InProduction_WithoutWire_Rejected() {
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        Map<String, Object> data = new HashMap<>();
        data.put("submerchant_id", "KNOWN_SM_ID");
        data.put("email", "known@restaurant.com");

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "MERCHANT_KYC_APPROVAL");
        payload.put("data", data);

        EasebuzzSubMerchant sm = new EasebuzzSubMerchant();
        sm.setSubMerchantId("KNOWN_SM_ID");
        when(subMerchantRepo.findBySubMerchantId("KNOWN_SM_ID")).thenReturn(Optional.of(sm));

        Map<String, Object> result = webhookService.handleSubMerchantWebhook(payload);
        assertEquals("hash_mismatch", result.get("status"));
        verify(subMerchantService, never()).processWebhook(any());
    }

    // --- Helpers ---

    private String generateReverseHash(Map<String, String> p) throws Exception {
        // salt|status|udf10|udf9|udf8|udf7|udf6|udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key
        StringBuilder sb = new StringBuilder();
        sb.append(TEST_SALT).append("|");
        sb.append(ns(p.get("status"))).append("|");
        sb.append(ns(p.get("udf10"))).append("|");
        sb.append(ns(p.get("udf9"))).append("|");
        sb.append(ns(p.get("udf8"))).append("|");
        sb.append(ns(p.get("udf7"))).append("|");
        sb.append(ns(p.get("udf6"))).append("|");
        sb.append(ns(p.get("udf5"))).append("|");
        sb.append(ns(p.get("udf4"))).append("|");
        sb.append(ns(p.get("udf3"))).append("|");
        sb.append(ns(p.get("udf2"))).append("|");
        sb.append(ns(p.get("udf1"))).append("|");
        sb.append(ns(p.get("email"))).append("|");
        sb.append(ns(p.get("firstname"))).append("|");
        sb.append(ns(p.get("productinfo"))).append("|");
        sb.append(ns(p.get("amount"))).append("|");
        sb.append(ns(p.get("txnid"))).append("|");
        sb.append(TEST_KEY);
        return sha512(sb.toString());
    }

    private String ns(String s) { return s != null ? s : ""; }

    private String sha512(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte b : digest) hash.append(String.format("%02x", b));
        return hash.toString();
    }
}
