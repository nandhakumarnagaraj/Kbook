package com.khanabook.saas.payment.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.payment.entity.Payment;
import com.khanabook.saas.payment.entity.PaymentGateway;
import com.khanabook.saas.payment.entity.PaymentStatus;
import com.khanabook.saas.payment.entity.RefundMode;
import com.khanabook.saas.payment.entity.RefundStatus;
import com.khanabook.saas.payment.entity.PaymentWebhookLog;
import com.khanabook.saas.payment.entity.RestaurantPaymentConfig;
import com.khanabook.saas.payment.repository.PaymentRepository;
import com.khanabook.saas.payment.repository.PaymentWebhookLogRepository;
import com.khanabook.saas.payment.repository.RestaurantPaymentConfigRepository;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.repository.BillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EasebuzzPaymentServiceIntegrationTest extends BaseIntegrationTest {

    private static final Long RESTAURANT_ID = 7101L;
    private static final String DEVICE_ID = "TEST_DEVICE";
    private static final String MERCHANT_KEY = "merchant-key-7101";
    private static final String SALT = "merchant-salt-7101";
    private static final String TXN_ID = "KB_7101_9001_1234567890";

    @Autowired private EasebuzzPaymentService paymentService;
    @Autowired private EasebuzzHashService hashService;
    @Autowired private CryptoService cryptoService;
    @Autowired private RestaurantPaymentConfigRepository configRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentWebhookLogRepository webhookLogRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private BillPaymentRepository billPaymentRepository;
    @MockBean private EasebuzzGatewayClient gatewayClient;

    @BeforeEach
    void setUp() {
        webhookLogRepository.deleteAll();
        paymentRepository.deleteAll();
        billPaymentRepository.deleteAll();
        billRepository.deleteAll();
        configRepository.deleteAll();
        userRepository.deleteAll();
        restaurantProfileRepository.deleteAll();
    }

    @Test
    void processGatewayCallback_marksPaymentAndBillSuccessful_andCreatesBillPayment() {
        Bill bill = saveDraftBill();
        saveActiveConfig();
        Payment payment = savePendingPayment(bill);

        Map<String, String> callback = signedCallback("success");

        Map<String, Object> result = paymentService.processGatewayCallback(callback);

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();
        List<PaymentWebhookLog> logs = webhookLogRepository.findAll();

        assertThat(result).containsEntry("ok", true);
        assertThat(updatedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(updatedPayment.getGatewayStatus()).isEqualTo("success");
        assertThat(updatedPayment.getGatewayPaymentId()).isEqualTo("EZP123");
        assertThat(updatedPayment.getVerifiedAt()).isNotNull();
        assertThat(updatedPayment.getFailureReason()).isNull();

        assertThat(updatedBill.getPaymentStatus()).isEqualTo("success");
        assertThat(updatedBill.getOrderStatus()).isEqualTo("completed");
        assertThat(updatedBill.getPaidAt()).isNotNull();
        assertThat(updatedBill.getCancelReason()).isEmpty();

        assertThat(billPaymentRepository.existsByRestaurantIdAndGatewayTxnId(RESTAURANT_ID, TXN_ID)).isTrue();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getSignatureValid()).isTrue();
        assertThat(logs.get(0).getProcessed()).isTrue();
    }

    @Test
    void processGatewayCallback_doesNotDowngradeSuccessfulPayment_whenStaleFailureArrivesLater() {
        Bill bill = saveDraftBill();
        saveActiveConfig();
        Payment payment = savePendingPayment(bill);

        paymentService.processGatewayCallback(signedCallback("success"));
        Map<String, Object> staleResult = paymentService.processGatewayCallback(signedCallback("failure"));

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();
        List<PaymentWebhookLog> logs = webhookLogRepository.findAll();

        assertThat(staleResult).containsEntry("ok", true).containsEntry("ignored", true);
        assertThat(updatedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(updatedPayment.getGatewayStatus()).isEqualTo("success");
        assertThat(updatedBill.getPaymentStatus()).isEqualTo("success");
        assertThat(updatedBill.getOrderStatus()).isEqualTo("completed");
        assertThat(logs).hasSize(2);
        assertThat(logs).allMatch(log -> Boolean.TRUE.equals(log.getProcessed()));
        assertThat(logs).allMatch(log -> Boolean.TRUE.equals(log.getSignatureValid()));
    }

    @Test
    void markManualRefund_recordsRefundForCancelledOfflineUpiBill_withoutEasebuzzPayment() {
        Bill bill = saveDraftBill();
        bill.setPaymentStatus("success");
        bill.setOrderStatus("cancelled");
        bill.setPaymentMode("upi");
        bill.setCancelReason("Customer requested cancellation");
        billRepository.save(bill);

        paymentService.markManualRefund(RESTAURANT_ID, bill.getId(), new BigDecimal("262.50"), "Sent by UPI");

        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();

        assertThat(paymentRepository.findByRestaurantIdAndBillIdOrderByCreatedAtDesc(RESTAURANT_ID, bill.getId())).isEmpty();
        assertThat(updatedBill.getOrderStatus()).isEqualTo("cancelled");
        assertThat(updatedBill.getPaymentStatus()).isEqualTo("success");
        assertThat(updatedBill.getRefundAmount()).isEqualByComparingTo("262.50");
        assertThat(updatedBill.getCancelReason()).isEqualTo("Sent by UPI");
    }

    @Test
    void markManualRefund_rejectsSuccessfulEasebuzzPayment() {
        Bill bill = saveDraftBill();
        bill.setPaymentStatus("success");
        bill.setOrderStatus("cancelled");
        bill.setPaymentMode("easebuzz");
        billRepository.save(bill);

        Payment payment = savePendingPayment(bill);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId("EZP123");
        payment.setGatewayStatus("success");
        payment.setVerifiedAt(System.currentTimeMillis());
        paymentRepository.save(payment);

        assertThatThrownBy(() -> paymentService.markManualRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("262.50"),
                "Manual fallback"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Easebuzz payments must be refunded via Easebuzz");
    }

    @Test
    void initiateGatewayRefund_rejectedByEasebuzz_marksRefundFailed_withoutCancellingBill() {
        Bill bill = saveDraftBill();
        bill.setPaymentStatus("success");
        bill.setOrderStatus("completed");
        billRepository.save(bill);
        saveActiveConfig();

        Payment payment = savePendingPayment(bill);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId("EZP123");
        payment.setGatewayStatus("success");
        payment.setVerifiedAt(System.currentTimeMillis());
        paymentRepository.save(payment);

        when(gatewayClient.initiateRefund(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EasebuzzGatewayClient.RefundInitiation.builder()
                        .apiStatus("0")
                        .apiAccepted(false)
                        .message("Refund rejected by gateway")
                        .build());

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("10"),
                "Customer requested cancellation"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund rejected by gateway");

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();

        assertThat(updatedPayment.getRefundMode()).isEqualTo(RefundMode.EASEBUZZ);
        assertThat(updatedPayment.getRefundStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(updatedPayment.getRefundAmount()).isEqualByComparingTo("10.00");
        assertThat(updatedBill.getOrderStatus()).isEqualTo("completed");
        assertThat(updatedBill.getPaymentStatus()).isEqualTo("success");
        assertThat(updatedBill.getRefundAmount()).isEqualByComparingTo("0");
    }

    @Test
    void initiateGatewayRefund_reusesMerchantRefundId_forSameRetryRequest() {
        Bill bill = saveDraftBill();
        bill.setPaymentStatus("success");
        bill.setOrderStatus("completed");
        billRepository.save(bill);
        saveActiveConfig();

        Payment payment = savePendingPayment(bill);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId("EZP123");
        payment.setGatewayStatus("success");
        payment.setVerifiedAt(System.currentTimeMillis());
        payment.setRefundMode(RefundMode.EASEBUZZ);
        payment.setRefundStatus(RefundStatus.FAILED);
        payment.setRefundAmount(new BigDecimal("10.00"));
        payment.setRefundReason("Customer requested cancellation");
        payment.setMerchantRefundId("KBR_EXISTING");
        paymentRepository.save(payment);

        when(gatewayClient.initiateRefund(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EasebuzzGatewayClient.RefundInitiation.builder()
                        .apiStatus("1")
                        .apiAccepted(true)
                        .refundStatus("pending")
                        .refundId("RF123")
                        .message("Request accepted")
                        .build());

        Payment updatedPayment = paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("10"),
                "Customer requested cancellation"
        );

        assertThat(updatedPayment.getMerchantRefundId()).isEqualTo("KBR_EXISTING");
        assertThat(updatedPayment.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(updatedPayment.getRefundAmount()).isEqualByComparingTo("10.00");
        verify(gatewayClient, never()).fetchRefundStatus(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void initiateGatewayRefund_rejectsDuplicateClickWhileRefundPending() {
        Bill bill = saveCompletedEasebuzzBill();
        Payment payment = saveSuccessfulEasebuzzPayment(bill);
        payment.setRefundMode(RefundMode.EASEBUZZ);
        payment.setRefundStatus(RefundStatus.PENDING);
        payment.setRefundAmount(new BigDecimal("100.00"));
        payment.setMerchantRefundId("KBR_PENDING");
        paymentRepository.save(payment);

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("100.00"),
                "Duplicate click"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already pending");

        verify(gatewayClient, never()).initiateRefund(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void initiateGatewayRefund_rejectsSecondRefundAfterPartialRefundSucceeded() {
        Bill bill = saveCompletedEasebuzzBill();
        Payment payment = saveSuccessfulEasebuzzPayment(bill);
        payment.setRefundMode(RefundMode.EASEBUZZ);
        payment.setRefundStatus(RefundStatus.SUCCESS);
        payment.setRefundAmount(new BigDecimal("100.00"));
        payment.setRefundReason("Partial refund processed");
        payment.setMerchantRefundId("KBR_PARTIAL");
        paymentRepository.save(payment);

        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();
        updatedBill.setRefundAmount(new BigDecimal("100.00"));
        updatedBill.setOrderStatus("cancelled");
        updatedBill.setCancelReason("Partial refund processed");
        billRepository.save(updatedBill);

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("50.00"),
                "Second refund attempt"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already refunded");
    }

    @Test
    void initiateGatewayRefund_rejectsRefundGreaterThanOrderAmount() {
        Bill bill = saveCompletedEasebuzzBill();
        saveSuccessfulEasebuzzPayment(bill);

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("300.00"),
                "Over refund"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed Easebuzz paid amount");
    }

    @Test
    void initiateGatewayRefund_rejectsRefundGreaterThanEasebuzzPaidAmount_forPartPayment() {
        Bill bill = saveCompletedEasebuzzBill();
        Payment payment = saveSuccessfulEasebuzzPayment(bill);
        payment.setAmount(new BigDecimal("100.00"));
        paymentRepository.save(payment);

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("150.00"),
                "Refund part payment"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed Easebuzz paid amount");
    }

    @Test
    void initiateGatewayRefund_rejectsAlreadyRefundedOrder() {
        Bill bill = saveCompletedEasebuzzBill();
        Payment payment = saveSuccessfulEasebuzzPayment(bill);
        payment.setRefundMode(RefundMode.EASEBUZZ);
        payment.setRefundStatus(RefundStatus.SUCCESS);
        payment.setRefundAmount(new BigDecimal("262.50"));
        payment.setMerchantRefundId("KBR_DONE");
        paymentRepository.save(payment);

        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();
        updatedBill.setRefundAmount(new BigDecimal("262.50"));
        updatedBill.setOrderStatus("cancelled");
        billRepository.save(updatedBill);

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("262.50"),
                "Already refunded"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already refunded");
    }

    @Test
    void initiateGatewayRefund_marksFailedWhenGatewayNetworkFails() {
        Bill bill = saveCompletedEasebuzzBill();
        Payment payment = saveSuccessfulEasebuzzPayment(bill);

        when(gatewayClient.initiateRefund(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("Connection reset"));

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("10.00"),
                "Network failure"
        )).isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Connection reset");

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();

        assertThat(updatedPayment.getRefundStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(updatedPayment.getRefundReason()).contains("Connection reset");
        assertThat(updatedBill.getOrderStatus()).isEqualTo("completed");
        assertThat(updatedBill.getRefundAmount()).isEqualByComparingTo("0");
    }

    @Test
    void initiateGatewayRefund_marksFailedWhenGatewayTimesOut() {
        Bill bill = saveCompletedEasebuzzBill();
        Payment payment = saveSuccessfulEasebuzzPayment(bill);

        when(gatewayClient.initiateRefund(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("10.00"),
                "Timeout"
        )).isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Read timed out");

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getRefundStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(updatedPayment.getRefundReason()).contains("Read timed out");
    }

    @Test
    void initiateGatewayRefund_marksFailedWhenCredentialsAreWrong() {
        Bill bill = saveCompletedEasebuzzBill();
        Payment payment = saveSuccessfulEasebuzzPayment(bill);

        when(gatewayClient.initiateRefund(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EasebuzzGatewayClient.RefundInitiation.builder()
                        .apiStatus("0")
                        .apiAccepted(false)
                        .message("Authentication failed: invalid key or hash")
                        .rawPayload("{\"status\":0,\"message\":\"Authentication failed: invalid key or hash\"}")
                        .build());

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("10.00"),
                "Wrong credentials"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authentication failed");

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getRefundStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(updatedPayment.getRefundLastGatewayPayload()).contains("Authentication failed");
    }

    @Test
    void initiateGatewayRefund_marksFailedWhenEnvironmentDoesNotMatch() {
        Bill bill = saveCompletedEasebuzzBill();
        Payment payment = saveSuccessfulEasebuzzPayment(bill);

        when(gatewayClient.initiateRefund(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EasebuzzGatewayClient.RefundInitiation.builder()
                        .apiStatus("0")
                        .apiAccepted(false)
                        .message("Transaction not found in selected environment")
                        .rawPayload("{\"status\":0,\"message\":\"Transaction not found in selected environment\"}")
                        .build());

        assertThatThrownBy(() -> paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("10.00"),
                "Env mismatch"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selected environment");

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getRefundStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(updatedPayment.getRefundReason()).contains("selected environment");
    }

    @Test
    void initiateGatewayRefund_acceptsEasebuzzRequestIdStyleResponse() {
        Bill bill = saveCompletedEasebuzzBill();
        Payment payment = saveSuccessfulEasebuzzPayment(bill);

        when(gatewayClient.initiateRefund(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EasebuzzGatewayClient.RefundInitiation.builder()
                        .apiStatus("0")
                        .apiAccepted(true)
                        .refundStatus("pending")
                        .refundId("RFZ13Y3BAX")
                        .message("Refund initiated, Your Request Id:RFZ13Y3BAX")
                        .rawPayload("{\"status\":0,\"message\":\"Refund initiated, Your Request Id:RFZ13Y3BAX\"}")
                        .build());

        Payment updatedPayment = paymentService.initiateGatewayRefund(
                RESTAURANT_ID,
                bill.getId(),
                new BigDecimal("10.00"),
                "Customer requested cancellation"
        );

        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();
        assertThat(updatedPayment.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(updatedPayment.getRefundGatewayRefundId()).isEqualTo("RFZ13Y3BAX");
        assertThat(updatedPayment.getRefundReason()).isEqualTo("Customer requested cancellation");
        assertThat(updatedBill.getOrderStatus()).isEqualTo("completed");
        assertThat(updatedBill.getRefundAmount()).isEqualByComparingTo("0");
    }

    private Bill saveCompletedEasebuzzBill() {
        Bill bill = saveDraftBill();
        bill.setPaymentStatus("success");
        bill.setOrderStatus("completed");
        bill.setPaymentMode("easebuzz_upi");
        billRepository.save(bill);
        saveActiveConfig();
        return bill;
    }

    private Payment saveSuccessfulEasebuzzPayment(Bill bill) {
        Payment payment = savePendingPayment(bill);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId("EZP123");
        payment.setGatewayStatus("success");
        payment.setVerifiedAt(System.currentTimeMillis());
        return paymentRepository.save(payment);
    }

    private RestaurantPaymentConfig saveActiveConfig() {
        long now = System.currentTimeMillis();
        RestaurantPaymentConfig config = new RestaurantPaymentConfig();
        config.setRestaurantId(RESTAURANT_ID);
        config.setGatewayName(PaymentGateway.EASEBUZZ);
        config.setMerchantKey(MERCHANT_KEY);
        config.setEncryptedSalt(cryptoService.encrypt(SALT));
        config.setEnvironment("TEST");
        config.setIsActive(true);
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        return configRepository.save(config);
    }

    private Bill saveDraftBill() {
        persistUser("owner-7101@test.com", RESTAURANT_ID, com.khanabook.saas.entity.UserRole.OWNER);
        long now = System.currentTimeMillis();
        Bill bill = new Bill();
        bill.setLocalId(9001L);
        bill.setDeviceId(DEVICE_ID);
        bill.setRestaurantId(RESTAURANT_ID);
        bill.setUpdatedAt(now);
        bill.setCreatedAt(now);
        bill.setServerUpdatedAt(now);
        bill.setIsDeleted(false);
        bill.setDailyOrderId(11L);
        bill.setDailyOrderDisplay("11");
        bill.setLifetimeOrderId(9001L);
        bill.setOrderType("order");
        bill.setCustomerName("Test Customer");
        bill.setCustomerWhatsapp("9999999999");
        bill.setSubtotal(new BigDecimal("250.00"));
        bill.setGstPercentage(new BigDecimal("5.00"));
        bill.setCgstAmount(new BigDecimal("6.25"));
        bill.setSgstAmount(new BigDecimal("6.25"));
        bill.setCustomTaxAmount(BigDecimal.ZERO);
        bill.setTotalAmount(new BigDecimal("262.50"));
        bill.setPaymentMode("upi");
        bill.setPartAmount1(new BigDecimal("262.50"));
        bill.setPartAmount2(BigDecimal.ZERO);
        bill.setPaymentStatus("pending");
        bill.setOrderStatus("draft");
        bill.setCreatedBy(1L);
        bill.setPaidAt(null);
        bill.setLastResetDate("2026-04-26");
        bill.setCancelReason("");
        return billRepository.save(bill);
    }

    private Payment savePendingPayment(Bill bill) {
        long now = System.currentTimeMillis();
        Payment payment = new Payment();
        payment.setRestaurantId(RESTAURANT_ID);
        payment.setBillId(bill.getId());
        payment.setUserId(1L);
        payment.setAmount(bill.getTotalAmount());
        payment.setCurrency("INR");
        payment.setGateway(PaymentGateway.EASEBUZZ);
        payment.setGatewayTxnId(TXN_ID);
        payment.setGatewayStatus("initiated");
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod("UPI");
        payment.setCheckoutUrl("https://testpay.easebuzz.in/pay/test");
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        return paymentRepository.save(payment);
    }

    private Map<String, String> signedCallback(String status) {
        Bill bill = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT_ID, DEVICE_ID, 9001L).orElseThrow();
        String amount = bill.getTotalAmount().toPlainString();
        String email = "noreply@khanabook.app";
        String firstName = bill.getCustomerName();
        String productInfo = "Bill payment";
        String hash = hashService.buildWebhookHash(
                SALT,
                status,
                email,
                firstName,
                productInfo,
                amount,
                TXN_ID,
                MERCHANT_KEY
        );
        return Map.of(
                "status", status,
                "email", email,
                "firstname", firstName,
                "productinfo", productInfo,
                "amount", amount,
                "txnid", TXN_ID,
                "key", MERCHANT_KEY,
                "easepayid", "EZP123",
                "hash", hash
        );
    }
}
