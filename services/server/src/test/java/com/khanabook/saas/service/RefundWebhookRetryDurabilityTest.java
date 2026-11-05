package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.RefundAttempt;
import com.khanabook.saas.feature.payments.data.RefundAttemptRepository;
import com.khanabook.saas.feature.payments.data.WebhookRetryJob;
import com.khanabook.saas.feature.payments.data.WebhookRetryJobRepository;
import com.khanabook.saas.feature.payments.service.EasebuzzWebhookService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * F4 gate. Proves or refutes, against a real transaction manager, whether a
 * REFUND retry job enqueued inside {@code handleRefundWebhook} survives when the
 * refund completion fails.
 *
 * <p>The fault is injected where Spring itself applies the rule: a persistence
 * failure inside a participating transaction marks that transaction
 * rollback-only, so writes made afterwards in the same transaction are discarded
 * at commit. The test therefore reproduces the production failure mode instead
 * of mocking the repository, which would delete the transaction semantics this
 * test exists to examine.
 *
 * <p>Outcome before any fix: {@code forcedPersistenceFailure_leavesDurableRetryJob}
 * fails with zero retry jobs - the catch runs, the enqueue is issued, the webhook
 * is acknowledged, and the row is still gone. If both tests pass, F4 is not a
 * defect and no production change is made.
 */
@Import(RefundWebhookRetryDurabilityTest.FaultInjectionConfig.class)
class RefundWebhookRetryDurabilityTest extends BaseIntegrationTest {

    private static final String AMOUNT = "500.00";
    private static final String MERCHANT_KEY = "TEST_MERCHANT_KEY";
    private static final String SALT = "TEST_SALT";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private EasebuzzWebhookService webhookService;
    @Autowired private BillRepository billRepo;
    @Autowired private RefundAttemptRepository refundAttemptRepo;
    @Autowired private EasebuzzWebhookEventRepository webhookEventRepo;
    @Autowired private WebhookRetryJobRepository retryRepo;
    @Autowired private FaultGate gate;

    @Test
    @DisplayName("control: a clean refund completion commits and creates no retry job")
    void control_happyPath_createsNoRetryJob() throws Exception {
        Fixture f = seed();
        Throwable thrown = catchThrowable(() -> webhookService.handleRefundWebhook(payload(f)));

        assertThat(thrown).isNull();
        assertThat(retryJobsFor(f)).as("no retry job for a successful completion").isZero();
        Optional<RefundAttempt> attempt = refundAttemptRepo.findByBillIdAndGatewayRefundId(f.billId, f.refundId);
        assertThat(attempt).isPresent();
        assertThat(attempt.get().getStatus()).isEqualTo("COMPLETED");
    }

    /**
     * F4 regression test.
     *
     * <p>Before the fix this failed with {@code ackFailure=none retryJobsForThisTxn=0
     * allRetryRows=0}: the refund completion rolled back, the REFUND retry row rolled back with
     * it, and nothing reached the caller. The refund mutation now runs in its own transaction and
     * {@code WebhookRetryService.enqueue} commits through REQUIRES_NEW, so the row survives the
     * rollback and the webhook is still acknowledged.
     */
    @Test
    @DisplayName("gate: a persistence failure must still leave a durable REFUND retry job")
    void forcedPersistenceFailure_leavesDurableRetryJob() throws Exception {
        Fixture f = seed();
        Map<String, String> payload = payload(f);
        gate.armed = true;
        Throwable ackFailure = catchThrowable(() -> webhookService.handleRefundWebhook(payload));
        gate.armed = false;

        long jobs = retryJobsFor(f);
        System.out.println("F4-DIAG txn=" + f.txn
                + " ackFailure=" + (ackFailure == null ? "none" : ackFailure.getClass().getName())
                + " retryJobsForThisTxn=" + jobs
                + " allRetryRows=" + retryRepo.findAll().size());

        assertThat(ackFailure)
                .as("the webhook must be acknowledged, not surface a rollback to Easebuzz")
                .isNull();
        assertThat(jobs)
                .as("a REFUND retry job must survive the failed completion")
                .isEqualTo(1);
    }

    /** Retry rows attributable to this fixture, so tests never see each other's rows. */
    private long retryJobsFor(Fixture f) {
        return retryRepo.findAll().stream()
                .map(WebhookRetryJob::getPayload)
                .filter(p -> p != null && p.contains(f.txn))
                .count();
    }

    // ---- seeding ----------------------------------------------------------

    static class Fixture {
        long rid;
        long localId;
        String deviceId;
        String txn;
        String epay;
        String refundId;
        long billId;
    }

    private Fixture seed() {
        int n = SEQ.incrementAndGet();
        long now = System.currentTimeMillis();

        Fixture f = new Fixture();
        f.rid = 9000L + n;
        f.localId = n;
        f.deviceId = "f4-dev-" + n;
        f.txn = "KBF4TXN" + n;
        f.epay = "EPAY4T" + n;
        f.refundId = "RFD4T" + n;

        Bill bill = new Bill();
        bill.setRestaurantId(f.rid);
        bill.setDeviceId(f.deviceId);
        bill.setLocalId(f.localId);
        bill.setCreatedAt(now);
        bill.setUpdatedAt(now);
        bill.setDailyOrderId((long) n);
        bill.setLastResetDate("2026-11-04");
        bill.setOrderType("dine_in");
        bill.setSubtotal(new BigDecimal(AMOUNT));
        bill.setTotalAmount(new BigDecimal(AMOUNT));
        bill.setPaymentMode("easebuzz");
        bill.setPaymentStatus("paid");
        bill.setOrderStatus("completed");
        bill.setStatusVersion(0);
        bill.setInventoryDeducted(false);
        bill.setPublicToken(UUID.randomUUID());
        bill.setGatewayTxnId(f.txn);
        bill.setGatewayStatus("success");
        bill.setPaidAt(now);
        bill = billRepo.save(bill);
        f.billId = bill.getId();

        RefundAttempt attempt = new RefundAttempt();
        attempt.setBillId(f.billId);
        attempt.setRestaurantId(f.rid);
        attempt.setGatewayTxnId(f.txn);
        attempt.setEasebuzzPaymentId(f.epay);
        attempt.setMerchantRefundId("MRF4T" + n);
        attempt.setGatewayRefundId(f.refundId);
        attempt.setAmount(new BigDecimal(AMOUNT));
        attempt.setStatus("INITIATED");
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        refundAttemptRepo.save(attempt);

        EasebuzzWebhookEvent event = new EasebuzzWebhookEvent();
        event.setRestaurantId(f.rid);
        event.setTxnId(f.txn);
        event.setEasebuzzId(f.epay);
        event.setStatus("success");
        event.setAmount(new BigDecimal(AMOUNT));
        event.setReceivedAt(now);
        webhookEventRepo.save(event);

        return f;
    }

    private Map<String, String> payload(Fixture f) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("txnid", f.txn);
        p.put("easepayid", f.epay);
        p.put("refund_id", f.refundId);
        p.put("refund_amount", AMOUNT);
        p.put("status", "processed");
        p.put("hash", sha512(MERCHANT_KEY + "|" + f.epay + "|" + SALT));
        return p;
    }

    private static String sha512(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-512").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ---- fault injection --------------------------------------------------

    static class FaultGate {
        volatile boolean armed = false;
    }

    @TestConfiguration
    @Import(FaultGate.class)
    static class FaultInjectionConfig {
        @Bean
        @Primary
        RefundAttemptRepository faultingRefundAttemptRepository(RefundAttemptRepository real, FaultGate gate) {
            return (RefundAttemptRepository) Proxy.newProxyInstance(
                    RefundAttemptRepository.class.getClassLoader(),
                    new Class<?>[]{RefundAttemptRepository.class},
                    (proxy, method, args) -> {
                        if (gate.armed && "save".equals(method.getName())) {
                            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                            throw new DataIntegrityViolationException(
                                    "F4 fault injection: forced persistence failure inside the refund transaction");
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    });
        }
    }
}
