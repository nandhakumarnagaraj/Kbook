package com.khanabook.saas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.compliance.data.FssaiRenewal;
import com.khanabook.saas.feature.compliance.data.FssaiRenewalRepository;
import com.khanabook.saas.feature.compliance.data.FssaiTracker;
import com.khanabook.saas.feature.compliance.data.FssaiTrackerRepository;
import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.feature.payments.data.EasebuzzPayout;
import com.khanabook.saas.feature.payments.data.EasebuzzPayoutRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.RefundAttempt;
import com.khanabook.saas.feature.payments.data.RefundAttemptRepository;
import com.khanabook.saas.feature.payments.service.EasebuzzProperties;
import com.khanabook.saas.feature.payments.service.EasebuzzWebhookService;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import com.khanabook.saas.feature.payments.service.WebhookRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EasebuzzWebhookService}: the security-critical webhook
 * ingestion surface. Every callback that can move money or flip a bill's state
 * must pass hash verification first, and every state transition must be
 * idempotent under duplicate deliveries.
 *
 * <p>Hashes are recomputed here with the same sequences the service documents,
 * so a regression in the hash order or inputs fails loudly instead of silently
 * accepting spoofed deliveries.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EasebuzzWebhookServiceTest {

    private static final String MERCHANT_KEY = "TEST_MERCHANT_KEY";
    private static final String SALT = "TEST_SALT";
    private static final String TXNID = "KBF00001ABCD1234";
    private static final String EASEBUZZ_ID = "EZPAY000111";
    private static final Long RESTAURANT_ID = 100L;
    private static final Long BILL_ID = 1L;
    private static final String FSSAI_NUMBER = "12345678901234";

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EasebuzzWebhookService service;

    @BeforeEach
    void setUp() {
        EasebuzzProperties props = new EasebuzzProperties();
        props.setMerchantKey(MERCHANT_KEY);
        props.setSalt(SALT);
        service = new EasebuzzWebhookService(
                billRepo, subMerchantService, subMerchantRepo, webhookEventRepo,
                refundAttemptRepo, props, payoutRepo, env, pushNotificationService,
                fssaiRenewalRepo, fssaiTrackerRepo, webhookRetryService, objectMapper);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
    }

    // ── Hash helpers (mirror the service's documented sequences) ──────────────

    private static String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }

    private String sha512(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte b : digest) {
            hash.append(String.format("%02x", b));
        }
        return hash.toString();
    }

    /** Reversed payment webhook hash: sha512(salt|status|udf10..udf1|email|firstname|productinfo|amount|txnid|key). */
    private String paymentHash(Map<String, String> p) throws Exception {
        String input = SALT + "|"
                + nullSafe(p.get("status")) + "|"
                + nullSafe(p.get("udf10")) + "|"
                + nullSafe(p.get("udf9")) + "|"
                + nullSafe(p.get("udf8")) + "|"
                + nullSafe(p.get("udf7")) + "|"
                + nullSafe(p.get("udf6")) + "|"
                + nullSafe(p.get("udf5")) + "|"
                + nullSafe(p.get("udf4")) + "|"
                + nullSafe(p.get("udf3")) + "|"
                + nullSafe(p.get("udf2")) + "|"
                + nullSafe(p.get("udf1")) + "|"
                + nullSafe(p.get("email")) + "|"
                + nullSafe(p.get("firstname")) + "|"
                + nullSafe(p.get("productinfo")) + "|"
                + nullSafe(p.get("amount")) + "|"
                + nullSafe(p.get("txnid")) + "|"
                + MERCHANT_KEY;
        return sha512(input);
    }

    /** Refund webhook hash: sha512(key|easepayid|salt). */
    private String refundHash(String easepayid) throws Exception {
        return sha512(MERCHANT_KEY + "|" + easepayid.trim() + "|" + SALT);
    }

    /** Settlement payout hash: sha512(key|payout_id|salt). */
    private String settlementPayoutHash(String payoutId) throws Exception {
        return sha512(MERCHANT_KEY + "|" + nullSafe(payoutId) + "|" + SALT);
    }

    /** Transfer (Payout V2) hash: key|beneficiary_account_number|ifsc|upi_handle|request_number|amount|utr|status|salt. */
    private String transferPayoutHash(Map<String, String> p) throws Exception {
        String input = MERCHANT_KEY + "|"
                + nullSafe(p.get("beneficiary_account_number")) + "|"
                + nullSafe(p.get("ifsc")) + "|"
                + nullSafe(p.get("beneficiary_upi_handle")) + "|"
                + nullSafe(p.get("unique_request_number")) + "|"
                + nullSafe(p.get("amount")) + "|"
                + nullSafe(p.get("unique_transaction_reference")) + "|"
                + nullSafe(p.get("status")) + "|"
                + SALT;
        return sha512(input);
    }

    /** Sub-merchant hash: sha512(key|submerchant_id|salt). */
    private String subMerchantHash(String subMerchantId) throws Exception {
        return sha512(MERCHANT_KEY + "|" + subMerchantId + "|" + SALT);
    }

    // ── Payload / entity builders ─────────────────────────────────────────────

    private Map<String, String> paymentPayload() {
        Map<String, String> p = new HashMap<>();
        p.put("txnid", TXNID);
        p.put("status", "success");
        p.put("amount", "250.00");
        p.put("easebuzz_id", EASEBUZZ_ID);
        p.put("udf1", BILL_ID.toString());
        p.put("udf2", RESTAURANT_ID.toString());
        p.put("email", "customer@example.com");
        p.put("firstname", "Test");
        p.put("productinfo", "Order");
        return p;
    }

    private Bill bill(String paymentStatus) {
        Bill bill = new Bill();
        bill.setId(BILL_ID);
        bill.setRestaurantId(RESTAURANT_ID);
        bill.setTotalAmount(new BigDecimal("250.00"));
        bill.setPaymentStatus(paymentStatus);
        bill.setGatewayTxnId(TXNID);
        bill.setDailyOrderDisplay("A1-01");
        bill.setRefundAmount(BigDecimal.ZERO);
        return bill;
    }

    private RefundAttempt attempt(String refundId, String amount, String status) {
        RefundAttempt attempt = new RefundAttempt();
        attempt.setBillId(BILL_ID);
        attempt.setGatewayRefundId(refundId);
        attempt.setEasebuzzPaymentId(EASEBUZZ_ID);
        attempt.setGatewayTxnId(TXNID);
        attempt.setAmount(new BigDecimal(amount));
        attempt.setStatus(status);
        return attempt;
    }

    // ── Payment webhook: hash verification ────────────────────────────────────

    @Test
    @DisplayName("Valid hash marks the bill paid, settles the amount, and enqueues POST_SPLIT")
    void validHash_marksBillPaidAndEnqueuesPostSplit() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("hash", paymentHash(payload));

        Bill bill = bill("pending");
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "success")).thenReturn(false);
        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));
        when(billRepo.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = service.handlePaymentWebhook(payload);

        assertThat(result.get("status")).isEqualTo("received");
        assertThat(bill.getPaymentStatus()).isEqualTo("paid");
        assertThat(bill.getGatewayStatus()).isEqualTo("success");
        assertThat(bill.getGatewayTxnId()).isEqualTo(TXNID);
        assertThat(bill.getPaidAt()).isNotNull();
        assertThat(bill.getSettledAmount()).isEqualByComparingTo("250.00");
        verify(webhookEventRepo).save(any(EasebuzzWebhookEvent.class));
        verify(pushNotificationService).pushToRestaurant(
                eq(RESTAURANT_ID), eq("Payment Received"), anyString(),
                eq("payment_received"), eq("1"), eq("bill"), any());
        verify(webhookRetryService).enqueueAt(
                eq("POST_SPLIT"), anyString(), anyLong(), eq("POST_SPLIT:1:" + EASEBUZZ_ID));
    }

    @Test
    @DisplayName("A hash signed over different values is rejected without side effects")
    void tamperedHash_returnsHashMismatchWithoutSideEffects() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("hash", paymentHash(payload));
        payload.put("amount", "300.00"); // tampered AFTER signing

        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "success")).thenReturn(false);

        Map<String, Object> result = service.handlePaymentWebhook(payload);

        assertThat(result.get("status")).isEqualTo("hash_mismatch");
        verify(billRepo, never()).save(any(Bill.class));
        verify(pushNotificationService, never())
                .pushToRestaurant(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A webhook with no hash is rejected")
    void missingHash_returnsHashMismatch() {
        Map<String, String> payload = paymentPayload();

        Map<String, Object> result = service.handlePaymentWebhook(payload);

        assertThat(result.get("status")).isEqualTo("hash_mismatch");
        verify(billRepo, never()).save(any());
    }

    @Test
    @DisplayName("Duplicate (txnid, status) short-circuits before hash verification — no double-processing")
    void duplicateWebhook_shortCircuitsBeforeHashVerification() {
        Map<String, String> payload = paymentPayload();
        // deliberately NO hash — proves the replay guard runs first

        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "success")).thenReturn(true);

        Map<String, Object> result = service.handlePaymentWebhook(payload);

        assertThat(result.get("status")).isEqualTo("ok");
        assertThat(result.get("message")).isEqualTo("already processed");
        verify(billRepo, never()).save(any());
    }

    // ── Payment webhook: state transitions ────────────────────────────────────

    @Test
    @DisplayName("Amount mismatch never marks the bill paid")
    void amountMismatch_doesNotMarkBillPaid() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("hash", paymentHash(payload));
        payload.put("amount", "300.00"); // bill total is 250.00

        Bill bill = bill("pending");
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "success")).thenReturn(false);
        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));

        Map<String, Object> result = service.handlePaymentWebhook(payload);

        assertThat(result.get("status")).isEqualTo("received");
        assertThat(bill.getPaymentStatus()).isEqualTo("pending");
        verify(billRepo, never()).save(any(Bill.class));
        verify(pushNotificationService, never())
                .pushToRestaurant(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("An already-paid bill skips a duplicate success webhook")
    void alreadyPaid_skipsDuplicateWebhook() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("hash", paymentHash(payload));

        Bill bill = bill("paid");
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "success")).thenReturn(false);
        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));

        Map<String, Object> result = service.handlePaymentWebhook(payload);

        assertThat(result.get("status")).isEqualTo("received");
        verify(billRepo, never()).save(any(Bill.class));
    }

    @Test
    @DisplayName("A udf1 billId that does not match the resolved bill blocks processing")
    void udf1BillIdMismatch_doesNotResolveBill() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("hash", paymentHash(payload));
        payload.put("udf1", "999");

        Bill bill = bill("pending");
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "success")).thenReturn(false);
        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));

        service.handlePaymentWebhook(payload);

        assertThat(bill.getPaymentStatus()).isEqualTo("pending");
        verify(billRepo, never()).save(any(Bill.class));
    }

    @Test
    @DisplayName("A udf2 restaurant mismatch blocks processing")
    void udf2RestaurantMismatch_doesNotResolveBill() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("hash", paymentHash(payload));
        payload.put("udf2", "999");

        Bill bill = bill("pending");
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "success")).thenReturn(false);
        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));

        service.handlePaymentWebhook(payload);

        assertThat(bill.getPaymentStatus()).isEqualTo("pending");
        verify(billRepo, never()).save(any(Bill.class));
    }

    @Test
    @DisplayName("auto refunded marks the bill failed with gateway_status auto_refunded")
    void autoRefunded_marksBillFailed() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("status", "auto refunded");
        payload.put("hash", paymentHash(payload));

        Bill bill = bill("pending");
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "auto refunded")).thenReturn(false);
        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));
        when(billRepo.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));

        service.handlePaymentWebhook(payload);

        assertThat(bill.getGatewayStatus()).isEqualTo("auto_refunded");
        assertThat(bill.getPaymentStatus()).isEqualTo("failed");
        verify(billRepo).save(bill);
    }

    @Test
    @DisplayName("auto refunded on a paid bill is treated as a possible reversal and skipped")
    void autoRefunded_onPaidBill_skipsReversal() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("status", "auto refunded");
        payload.put("hash", paymentHash(payload));

        Bill bill = bill("paid");
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "auto refunded")).thenReturn(false);
        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));

        service.handlePaymentWebhook(payload);

        assertThat(bill.getPaymentStatus()).isEqualTo("paid");
        verify(billRepo, never()).save(any(Bill.class));
    }

    @Test
    @DisplayName("expired clears the stale link so the POS can create a fresh one")
    void expiredWebhook_clearsStaleLinkForFreshRetry() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("status", "expired");
        payload.put("hash", paymentHash(payload));

        Bill bill = bill("link_sent");
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "expired")).thenReturn(false);
        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));
        when(billRepo.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));

        service.handlePaymentWebhook(payload);

        assertThat(bill.getGatewayTxnId()).isNull();
        assertThat(bill.getPaymentStatus()).isEqualTo("pending");
        assertThat(bill.getGatewayStatus()).isEqualTo("expired");
        verify(billRepo).save(bill);
    }

    @Test
    @DisplayName("failure records the gateway status on the bill")
    void failureWebhook_recordsGatewayStatus() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("status", "failure");
        payload.put("hash", paymentHash(payload));

        Bill bill = bill("pending");
        when(webhookEventRepo.existsByTxnIdAndStatus(TXNID, "failure")).thenReturn(false);
        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));
        when(billRepo.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));

        service.handlePaymentWebhook(payload);

        assertThat(bill.getGatewayStatus()).isEqualTo("failure");
        verify(billRepo).save(bill);
    }

    // ── FSSAI renewal ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("fssai_renewal success updates the renewal and tracker, never a bill")
    void fssaiRenewalSuccess_updatesRenewalAndTracker() throws Exception {
        Map<String, String> payload = paymentPayload();
        payload.put("udf1", "fssai_renewal");
        payload.put("hash", paymentHash(payload));

        FssaiRenewal renewal = new FssaiRenewal();
        renewal.setRestaurantId(RESTAURANT_ID);
        renewal.setFssaiNumber(FSSAI_NUMBER);
        renewal.setYears(2);
        renewal.setAmount(new BigDecimal("2000.00"));
        renewal.setStatus("PENDING");
        renewal.setEasebuzzTxnId(TXNID);

        FssaiTracker tracker = new FssaiTracker();
        tracker.setRestaurantId(RESTAURANT_ID);
        tracker.setStatus("RENEWAL_INITIATED");

        when(fssaiRenewalRepo.findByEasebuzzTxnId(TXNID)).thenReturn(Optional.of(renewal));
        when(fssaiTrackerRepo.findByRestaurantId(RESTAURANT_ID)).thenReturn(Optional.of(tracker));
        when(fssaiRenewalRepo.save(any(FssaiRenewal.class))).thenAnswer(i -> i.getArgument(0));
        when(fssaiTrackerRepo.save(any(FssaiTracker.class))).thenAnswer(i -> i.getArgument(0));

        service.handlePaymentWebhook(payload);

        assertThat(renewal.getStatus()).isEqualTo("SUCCESS");
        assertThat(tracker.getStatus()).isEqualTo("RENEWAL_PAID");
        verify(billRepo, never()).save(any(Bill.class));
        verify(pushNotificationService).pushToRestaurant(
                eq(RESTAURANT_ID), eq("FSSAI Renewal Paid"), anyString(),
                eq("system"), eq(FSSAI_NUMBER), eq("fssai"), any());
    }

    // ── Refund webhook ────────────────────────────────────────────────────────

    @Test
    @DisplayName("A matched refunded callback completes the attempt and records partial refund")
    void refundWebhook_validHash_completesPartialRefund() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", TXNID);
        payload.put("status", "refunded");
        payload.put("easepayid", EASEBUZZ_ID);
        payload.put("refund_id", "RFD-1");
        payload.put("refund_amount", "100.00");
        payload.put("hash", refundHash(EASEBUZZ_ID));

        Bill bill = bill("paid");
        RefundAttempt attempt = attempt("RFD-1", "100.00", "INITIATED");
        EasebuzzWebhookEvent paymentEvent = new EasebuzzWebhookEvent();
        paymentEvent.setEasebuzzId(EASEBUZZ_ID);

        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));
        when(webhookEventRepo.findByRestaurantIdAndTxnId(RESTAURANT_ID, TXNID))
                .thenReturn(Optional.of(paymentEvent));
        when(refundAttemptRepo.findByBillIdAndGatewayRefundId(BILL_ID, "RFD-1"))
                .thenReturn(Optional.of(attempt));
        when(billRepo.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        when(refundAttemptRepo.save(any(RefundAttempt.class))).thenAnswer(i -> i.getArgument(0));
        when(webhookEventRepo.save(any(EasebuzzWebhookEvent.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = service.handleRefundWebhook(payload);

        assertThat(result.get("status")).isEqualTo("received");
        assertThat(attempt.getStatus()).isEqualTo("COMPLETED");
        assertThat(bill.getRefundAmount()).isEqualByComparingTo("100.00");
        assertThat(bill.getRefundId()).isEqualTo("RFD-1");
        assertThat(bill.getPaymentStatus()).isEqualTo("partially_refunded");
        assertThat(bill.getGatewayStatus()).isEqualTo("partially_refunded");
        assertThat(bill.getOrderStatus()).isNotEqualTo("cancelled");
        verify(pushNotificationService).pushToRestaurant(
                eq(RESTAURANT_ID), eq("Refund Completed"), anyString(),
                eq("refund"), eq("1"), eq("bill"), any());
        verify(webhookEventRepo).save(any(EasebuzzWebhookEvent.class));
    }

    @Test
    @DisplayName("A full refund marks the bill refunded and cancels the order")
    void refundWebhook_fullRefund_marksBillRefundedAndCancelled() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", TXNID);
        payload.put("status", "refunded");
        payload.put("easepayid", EASEBUZZ_ID);
        payload.put("refund_id", "RFD-2");
        payload.put("refund_amount", "250.00");
        payload.put("hash", refundHash(EASEBUZZ_ID));

        Bill bill = bill("paid");
        RefundAttempt attempt = attempt("RFD-2", "250.00", "INITIATED");
        EasebuzzWebhookEvent paymentEvent = new EasebuzzWebhookEvent();
        paymentEvent.setEasebuzzId(EASEBUZZ_ID);

        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));
        when(webhookEventRepo.findByRestaurantIdAndTxnId(RESTAURANT_ID, TXNID))
                .thenReturn(Optional.of(paymentEvent));
        when(refundAttemptRepo.findByBillIdAndGatewayRefundId(BILL_ID, "RFD-2"))
                .thenReturn(Optional.of(attempt));
        when(billRepo.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        when(refundAttemptRepo.save(any(RefundAttempt.class))).thenAnswer(i -> i.getArgument(0));
        when(webhookEventRepo.save(any(EasebuzzWebhookEvent.class))).thenAnswer(i -> i.getArgument(0));

        service.handleRefundWebhook(payload);

        assertThat(bill.getRefundAmount()).isEqualByComparingTo("250.00");
        assertThat(bill.getPaymentStatus()).isEqualTo("refunded");
        assertThat(bill.getGatewayStatus()).isEqualTo("refunded");
        assertThat(bill.getOrderStatus()).isEqualTo("cancelled");
    }

    @Test
    @DisplayName("A refund_amount that differs from the recorded attempt sets review-required")
    void refundWebhook_amountMismatch_setsReviewRequired() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", TXNID);
        payload.put("status", "refunded");
        payload.put("easepayid", EASEBUZZ_ID);
        payload.put("refund_id", "RFD-1");
        payload.put("refund_amount", "500.00"); // attempt was 100.00
        payload.put("hash", refundHash(EASEBUZZ_ID));

        Bill bill = bill("paid");
        RefundAttempt attempt = attempt("RFD-1", "100.00", "INITIATED");
        EasebuzzWebhookEvent paymentEvent = new EasebuzzWebhookEvent();
        paymentEvent.setEasebuzzId(EASEBUZZ_ID);

        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));
        when(webhookEventRepo.findByRestaurantIdAndTxnId(RESTAURANT_ID, TXNID))
                .thenReturn(Optional.of(paymentEvent));
        when(refundAttemptRepo.findByBillIdAndGatewayRefundId(BILL_ID, "RFD-1"))
                .thenReturn(Optional.of(attempt));
        when(billRepo.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));

        service.handleRefundWebhook(payload);

        assertThat(bill.getGatewayStatus()).isEqualTo("refund_review_required");
        assertThat(attempt.getStatus()).isEqualTo("INITIATED");
        verify(refundAttemptRepo, never()).save(any(RefundAttempt.class));
    }

    @Test
    @DisplayName("A duplicate refund completion is ignored")
    void refundWebhook_duplicateCompletion_ignored() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", TXNID);
        payload.put("status", "refunded");
        payload.put("easepayid", EASEBUZZ_ID);
        payload.put("refund_id", "RFD-1");
        payload.put("refund_amount", "100.00");
        payload.put("hash", refundHash(EASEBUZZ_ID));

        Bill bill = bill("paid");
        RefundAttempt attempt = attempt("RFD-1", "100.00", "COMPLETED");
        EasebuzzWebhookEvent paymentEvent = new EasebuzzWebhookEvent();
        paymentEvent.setEasebuzzId(EASEBUZZ_ID);

        when(billRepo.findByGatewayTxnId(TXNID)).thenReturn(Optional.of(bill));
        when(webhookEventRepo.findByRestaurantIdAndTxnId(RESTAURANT_ID, TXNID))
                .thenReturn(Optional.of(paymentEvent));
        when(refundAttemptRepo.findByBillIdAndGatewayRefundId(BILL_ID, "RFD-1"))
                .thenReturn(Optional.of(attempt));

        service.handleRefundWebhook(payload);

        verify(refundAttemptRepo, never()).save(any(RefundAttempt.class));
        verify(billRepo, never()).save(any(Bill.class));
    }

    @Test
    @DisplayName("A refund webhook with a bad hash is rejected")
    void refundWebhook_invalidHash_returnsHashMismatch() {
        Map<String, String> payload = new HashMap<>();
        payload.put("txnid", TXNID);
        payload.put("easepayid", EASEBUZZ_ID);
        payload.put("hash", "deadbeef");

        Map<String, Object> result = service.handleRefundWebhook(payload);

        assertThat(result.get("status")).isEqualTo("hash_mismatch");
        verify(billRepo, never()).save(any());
    }

    // ── Payout webhook ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Settlement payout hash updates the payout to success with the UTR")
    void payoutWebhook_settlementHash_updatesPayout() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("payout_id", "PO-123");
        payload.put("unique_request_number", "REQ-1");
        payload.put("status", "1");
        payload.put("bank_transaction_id", "UTR-999");
        payload.put("hash", settlementPayoutHash("PO-123"));

        EasebuzzPayout payout = new EasebuzzPayout();
        payout.setRestaurantId(RESTAURANT_ID);
        payout.setAmount(new BigDecimal("500.00"));
        payout.setMerchantRequestId("REQ-1");

        when(payoutRepo.findByMerchantRequestId("REQ-1")).thenReturn(Optional.of(payout));
        when(payoutRepo.save(any(EasebuzzPayout.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = service.handlePayoutWebhook(Map.of("data", payload));

        assertThat(result.get("status")).isEqualTo("received");
        assertThat(payout.getStatus()).isEqualTo("success");
        assertThat(payout.getUtr()).isEqualTo("UTR-999");
        verify(payoutRepo).save(payout);
        verify(pushNotificationService).pushToRestaurant(
                eq(RESTAURANT_ID), eq("Payout Status Updated"), anyString(),
                eq("settlement"), any(), eq("payout"), any());
    }

    @Test
    @DisplayName("Transfer (Payout V2) hash updates the payout")
    void payoutWebhook_transferHash_updatesPayout() throws Exception {
        Map<String, String> inner = new HashMap<>();
        inner.put("beneficiary_account_number", "1234567890");
        inner.put("ifsc", "HDFC0000001");
        inner.put("beneficiary_upi_handle", "");
        inner.put("unique_request_number", "REQ-2");
        inner.put("amount", "1000.00");
        inner.put("unique_transaction_reference", "UTR-777");
        inner.put("status", "success");
        inner.put("hash", transferPayoutHash(inner));

        EasebuzzPayout payout = new EasebuzzPayout();
        payout.setRestaurantId(RESTAURANT_ID);
        payout.setAmount(new BigDecimal("1000.00"));

        when(payoutRepo.findByMerchantRequestId("REQ-2")).thenReturn(Optional.of(payout));
        when(payoutRepo.save(any(EasebuzzPayout.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = service.handlePayoutWebhook(Map.of("data", inner));

        assertThat(result.get("status")).isEqualTo("received");
        assertThat(payout.getStatus()).isEqualTo("success");
        assertThat(payout.getUtr()).isEqualTo("UTR-777");
    }

    @Test
    @DisplayName("A payout webhook with a tampered hash is rejected")
    void payoutWebhook_invalidHash_returnsHashMismatch() {
        Map<String, String> payload = new HashMap<>();
        payload.put("payout_id", "PO-123");
        payload.put("status", "1");
        payload.put("hash", "tampered");

        Map<String, Object> result = service.handlePayoutWebhook(Map.of("data", payload));

        assertThat(result.get("status")).isEqualTo("hash_mismatch");
        verify(payoutRepo, never()).save(any());
    }

    // ── Sub-merchant KYC webhook ──────────────────────────────────────────────

    @Test
    @DisplayName("A hashed sub-merchant webhook is verified strictly against key|submerchant_id|salt")
    void subMerchantWebhook_hashProvided_verifiedStrictly() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("submerchant_id", "KBSM-1");
        data.put("hash", subMerchantHash("KBSM-1"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "KYC_STATUS");
        payload.put("data", data);

        when(subMerchantRepo.findBySubMerchantId("KBSM-1"))
                .thenReturn(Optional.of(new EasebuzzSubMerchant()));

        Map<String, Object> result = service.handleSubMerchantWebhook(payload);

        assertThat(result.get("status")).isEqualTo("received");
        verify(subMerchantService).processWebhook(payload);
    }

    @Test
    @DisplayName("A sub-merchant webhook with a wrong hash is rejected")
    void subMerchantWebhook_hashMismatch_rejected() {
        Map<String, Object> data = new HashMap<>();
        data.put("submerchant_id", "KBSM-1");
        data.put("hash", "wrong-hash");
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", data);

        Map<String, Object> result = service.handleSubMerchantWebhook(payload);

        assertThat(result.get("status")).isEqualTo("hash_mismatch");
        verify(subMerchantService, never()).processWebhook(any());
    }

    @Test
    @DisplayName("Unhashed MERCHANT_KYC_APPROVAL is accepted in dev/sandbox (audited bypass)")
    void unhashedKycApproval_devProfile_acceptedWithAuditWarning() {
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "MERCHANT_KYC_APPROVAL");
        payload.put("submerchant_id", "KBSM-1");

        when(subMerchantRepo.findBySubMerchantId("KBSM-1"))
                .thenReturn(Optional.of(new EasebuzzSubMerchant()));

        Map<String, Object> result = service.handleSubMerchantWebhook(payload);

        assertThat(result.get("status")).isEqualTo("received");
        verify(subMerchantService).processWebhook(payload);
    }

    @Test
    @DisplayName("Unhashed MERCHANT_KYC_APPROVAL is rejected in production without WIRE verification")
    void unhashedKycApproval_productionRejectedWithoutWireVerification() {
        // env stays "prod" from setUp; wireApiClient is never injected (null)

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "MERCHANT_KYC_APPROVAL");
        payload.put("submerchant_id", "KBSM-1");

        when(subMerchantRepo.findBySubMerchantId("KBSM-1"))
                .thenReturn(Optional.of(new EasebuzzSubMerchant()));

        Map<String, Object> result = service.handleSubMerchantWebhook(payload);

        assertThat(result.get("status")).isEqualTo("hash_mismatch");
        verify(subMerchantService, never()).processWebhook(any());
    }

    @Test
    @DisplayName("Unhashed MERCHANT_KYC_APPROVAL in production is verified via the official WIRE API")
    void unhashedKycApproval_production_verifiedViaWireApi() {
        com.khanabook.saas.feature.payments.service.EasebuzzWireApiClient wireApi =
                mock(com.khanabook.saas.feature.payments.service.EasebuzzWireApiClient.class);
        when(wireApi.getSubMerchantByEmail(anyString()))
                .thenReturn(Map.of("success", true));
        ReflectionTestUtils.setField(service, "wireApiClient", wireApi);

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "MERCHANT_KYC_APPROVAL");
        payload.put("email", "owner@restaurant.com");

        when(subMerchantRepo.findByContactEmail("owner@restaurant.com"))
                .thenReturn(Optional.of(new EasebuzzSubMerchant()));

        Map<String, Object> result = service.handleSubMerchantWebhook(payload);

        assertThat(result.get("status")).isEqualTo("received");
        verify(subMerchantService).processWebhook(payload);
    }

    @Test
    @DisplayName("Unhashed MERCHANT_KYC_APPROVAL for an unknown entity is rejected")
    void unhashedKycApproval_unknownEntity_rejected() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "MERCHANT_KYC_APPROVAL");
        payload.put("submerchant_id", "UNKNOWN-SM");

        when(subMerchantRepo.findBySubMerchantId("UNKNOWN-SM")).thenReturn(Optional.empty());

        Map<String, Object> result = service.handleSubMerchantWebhook(payload);

        assertThat(result.get("status")).isEqualTo("hash_mismatch");
        verify(subMerchantService, never()).processWebhook(any());
    }
}
