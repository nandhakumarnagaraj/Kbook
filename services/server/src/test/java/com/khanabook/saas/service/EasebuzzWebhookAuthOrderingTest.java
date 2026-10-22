package com.khanabook.saas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.compliance.data.FssaiRenewalRepository;
import com.khanabook.saas.feature.compliance.data.FssaiTrackerRepository;
import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.feature.payments.data.EasebuzzPayoutRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.RefundAttemptRepository;
import com.khanabook.saas.feature.payments.service.EasebuzzProperties;
import com.khanabook.saas.feature.payments.service.EasebuzzWebhookService;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import com.khanabook.saas.feature.payments.service.WebhookRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the order of the two guards at the top of payment-webhook ingestion: authentication first,
 * replay detection second.
 *
 * <p>The duplicate lookup answers "have I seen this txnid/status before?", which is information
 * about the merchant's data. Answering it before the hash is checked gave an unauthenticated
 * caller a way to probe which transaction ids exist. These tests fail if that ordering returns.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EasebuzzWebhookAuthOrderingTest {

    private static final String MERCHANT_KEY = "TEST_MERCHANT_KEY";
    private static final String SALT = "TEST_SALT";
    private static final String TXNID = "KBF00001ABCD1234";

    @Mock private BillRepository billRepo;
    @Mock private SubMerchantService subMerchantService;
    @Mock private EasebuzzSubMerchantRepository subMerchantRepo;
    @Mock private EasebuzzWebhookEventRepository webhookEventRepo;
    @Mock private RefundAttemptRepository refundAttemptRepo;
    @Mock private EasebuzzPayoutRepository payoutRepo;
    @Mock private Environment env;
    @Mock private PushNotificationService pushNotificationService;
    @Mock private FssaiRenewalRepository fssaiRenewalRepo;
    @Mock private FssaiTrackerRepository fssaiTrackerRepo;
    @Mock private WebhookRetryService webhookRetryService;

    private EasebuzzWebhookService service;

    @BeforeEach
    void setUp() {
        EasebuzzProperties props = new EasebuzzProperties();
        props.setMerchantKey(MERCHANT_KEY);
        props.setSalt(SALT);
        service = new EasebuzzWebhookService(
                billRepo, subMerchantService, subMerchantRepo, webhookEventRepo,
                refundAttemptRepo, props, payoutRepo, env, pushNotificationService,
                fssaiRenewalRepo, fssaiTrackerRepo, webhookRetryService, new ObjectMapper());
    }

    private String sha512(String input) throws Exception {
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-512").digest(input.getBytes(StandardCharsets.UTF_8))) {
            hash.append(String.format("%02x", b));
        }
        return hash.toString();
    }

    private Map<String, String> payload(String hash) {
        Map<String, String> p = new HashMap<>();
        p.put("txnid", TXNID);
        p.put("status", "success");
        p.put("amount", "250.00");
        p.put("easebuzz_id", "EZPAY000111");
        p.put("udf1", "1");
        p.put("udf2", "100");
        p.put("hash", hash);
        return p;
    }

    /** Reverse payment hash: sha512(salt|status|udf10..udf1|email|firstname|productinfo|amount|txnid|key). */
    private String reverseHash(Map<String, String> p) throws Exception {
        StringBuilder sb = new StringBuilder(SALT).append("|").append(p.get("status"));
        for (int i = 10; i >= 1; i--) {
            String v = p.get("udf" + i);
            sb.append("|").append(v == null ? "" : v);
        }
        sb.append("|").append(p.getOrDefault("email", ""));
        sb.append("|").append(p.getOrDefault("firstname", ""));
        sb.append("|").append(p.getOrDefault("productinfo", ""));
        sb.append("|").append(p.get("amount"));
        sb.append("|").append(p.get("txnid"));
        sb.append("|").append(MERCHANT_KEY);
        return sha512(sb.toString());
    }

    @Test
    void rejectsAnUnsignedPayloadWithoutRevealingWhetherTheTransactionIsKnown() {
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "success")).thenReturn(true);

        Map<String, Object> result = service.handlePaymentWebhook(payload("0".repeat(128)));

        assertEquals("hash_mismatch", result.get("status"));
        verify(webhookEventRepo, never()).existsByTxnIdAndStatus(TXNID, "success");
    }

    @Test
    void keepsDuplicateDeliveriesOfAValidPayloadIdempotent() throws Exception {
        Map<String, String> p = payload(null);
        p.put("hash", reverseHash(p));
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "success")).thenReturn(true);

        Map<String, Object> result = service.handlePaymentWebhook(p);

        assertEquals("ok", result.get("status"));
        assertEquals("already processed", result.get("message"));
        verify(webhookEventRepo).existsByTxnIdAndStatus(TXNID, "success");
    }
}
