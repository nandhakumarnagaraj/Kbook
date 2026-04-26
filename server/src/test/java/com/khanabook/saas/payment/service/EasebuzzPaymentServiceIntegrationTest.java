package com.khanabook.saas.payment.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.payment.entity.Payment;
import com.khanabook.saas.payment.entity.PaymentGateway;
import com.khanabook.saas.payment.entity.PaymentStatus;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
