package com.khanabook.saas.service;

import com.khanabook.saas.config.EasebuzzProperties;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzPayoutRepository;
import com.khanabook.saas.repository.EasebuzzWebhookEventRepository;
import com.khanabook.saas.repository.FssaiRenewalRepository;
import com.khanabook.saas.repository.FssaiTrackerRepository;
import com.khanabook.saas.entity.FssaiRenewal;
import com.khanabook.saas.entity.FssaiTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
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
    @Mock private EasebuzzProperties props;
    @Mock private PostSplitService postSplitService;
    @Mock private SubMerchantService subMerchantService;
    @Mock private EasebuzzPayoutRepository payoutRepo;
    @Mock private PushNotificationService pushNotificationService;
    @Mock private FssaiRenewalRepository fssaiRenewalRepo;
    @Mock private FssaiTrackerRepository fssaiTrackerRepo;

    @InjectMocks
    private EasebuzzWebhookService webhookService;

    private final String TEST_KEY = "ADNX3KYX5";
    private final String TEST_SALT = "Z4UFP4939";

    @BeforeEach
    void setup() {
        lenient().when(props.getMerchantKey()).thenReturn(TEST_KEY);
        lenient().when(props.getSalt()).thenReturn(TEST_SALT);
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
        when(billRepo.findById(500L)).thenReturn(Optional.of(mockBill));

        // 3. Execute
        Map<String, Object> response = webhookService.handlePaymentWebhook(payload);

        // 4. Verify
        assertEquals("received", response.get("status"));
        assertEquals("paid", mockBill.getPaymentStatus());
        assertEquals("success", mockBill.getGatewayStatus());
        assertEquals("KB12345", mockBill.getGatewayTxnId());
        
        // Verify post-split was triggered
        verify(postSplitService, times(1)).createPostSplitAsync(eq(500L), eq("E250TEST"), eq("KB12345"));
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
