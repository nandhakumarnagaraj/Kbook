package com.khanabook.saas.feature.payments.service;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Closes the gap between "a payment link was created" and "the gateway told us what happened".
 *
 * <p>Until now the only automatic confirmation was the 06:00 reconciliation, so a customer who
 * paid but whose webhook was lost looked unpaid for up to a day, and a link nobody paid sat on
 * {@code link_created} forever. This sweep re-asks the gateway about links that have been open
 * for a few minutes, and expires the ones that are still unconfirmed after a longer threshold.
 *
 * <p>Design notes that matter at scale:
 * <ul>
 *   <li>Bounded work — one page per run, oldest first, so a backlog drains in the order customers
 *       are waiting and a single run can never scan the whole table.</li>
 *   <li>No long transaction — the gateway call happens between short transactions. Holding a row
 *       lock across a network call would let a slow gateway stall bill writes.</li>
 *   <li>Race-safe expiry — the update is guarded on the same conditions the sweep read, so a
 *       webhook that marks the bill paid in the meantime simply makes the expiry a no-op.</li>
 *   <li>Confirmation is delegated to {@link EasebuzzPaymentService#verifyPayment(Long)}, the same
 *       path the manual endpoint uses, so "paid" has exactly one definition.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StuckPaymentSweepService {

    private static final Logger log = LoggerFactory.getLogger(StuckPaymentSweepService.class);

    private final BillRepository billRepo;
    private final EasebuzzPaymentService paymentService;
    private final EasebuzzWebhookEventRepository webhookEventRepo;
    private final MeterRegistry meterRegistry;

    @Value("${payments.sweep.enabled:true}")
    private boolean enabled = true;

    /** Below this age a link is probably still being paid; leave it alone. */
    @Value("${payments.sweep.recheck-after-ms:300000}")
    private long recheckAfterMs = 300_000L;

    /** After this age an unconfirmed link is expired and the bill stops being "awaiting payment". */
    @Value("${payments.sweep.expire-after-ms:1800000}")
    private long expireAfterMs = 1_800_000L;

    @Value("${payments.sweep.batch-size:100}")
    private int batchSize = 100;

    /**
     * Seconds since the last webhook we accepted, or -1 if none has ever arrived.
     *
     * <p>This is the signal that "payments stopped" looks like "nobody bought anything": silence
     * from the gateway is indistinguishable from a quiet evening unless it is measured.
     */
    @PostConstruct
    void registerWebhookSilenceGauge() {
        Gauge.builder("payments.webhook.silence.seconds", this, StuckPaymentSweepService::secondsSinceLastWebhook)
                .description("Seconds since the last Easebuzz webhook; -1 when none has ever been received")
                .register(meterRegistry);
    }

    double secondsSinceLastWebhook() {
        try {
            long latest = webhookEventRepo.findLatestReceivedAt();
            if (latest <= 0) {
                return -1d;
            }
            return Math.max(0d, (System.currentTimeMillis() - latest) / 1000d);
        } catch (Exception e) {
            // A failing gauge must never break the scrape that reports every other metric.
            return -1d;
        }
    }

    @Scheduled(
            fixedDelayString = "${payments.sweep.interval-ms:120000}",
            initialDelayString = "${payments.sweep.initial-delay-ms:60000}")
    public void sweepUnconfirmedLinks() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Bill> candidates = billRepo.findUnconfirmedPaymentLinks(
                now - recheckAfterMs, PageRequest.of(0, Math.max(1, batchSize)));
        if (candidates.isEmpty()) {
            return;
        }

        int confirmed = 0;
        int expired = 0;
        int stillPending = 0;
        int failed = 0;

        for (Bill candidate : candidates) {
            Long billId = candidate.getId();
            try {
                paymentService.verifyPayment(billId);

                Bill fresh = billRepo.findById(billId).orElse(null);
                if (fresh == null || isSettled(fresh)) {
                    confirmed++;
                    meterRegistry.counter("payments.sweep.confirmed").increment();
                    continue;
                }

                Long linkCreatedAt = fresh.getGatewayLinkCreatedAt() != null
                        ? fresh.getGatewayLinkCreatedAt()
                        : candidate.getGatewayLinkCreatedAt();
                long ageMs = now - (linkCreatedAt != null ? linkCreatedAt : now);
                if (ageMs >= expireAfterMs) {
                    if (billRepo.expireUnconfirmedLink(billId, now) > 0) {
                        expired++;
                        meterRegistry.counter("payments.sweep.expired").increment();
                        log.warn("Expired unconfirmed payment link billId={} txnId={} ageMs={}",
                                billId, fresh.getGatewayTxnId(), ageMs);
                    } else {
                        // The bill moved on between the read and the update - most likely a
                        // webhook confirming it. Not an error.
                        stillPending++;
                    }
                } else {
                    stillPending++;
                    meterRegistry.counter("payments.sweep.still_pending").increment();
                }
            } catch (Exception e) {
                failed++;
                meterRegistry.counter("payments.sweep.failed").increment();
                // One bad bill must not stop the sweep: the rest of the estate still needs it.
                log.warn("Sweep could not confirm billId={}: {}", billId, e.getMessage());
            }
        }

        log.info("Payment sweep finished: {} checked, {} confirmed, {} expired, {} still pending, {} failed",
                candidates.size(), confirmed, expired, stillPending, failed);
    }

    private static boolean isSettled(Bill bill) {
        return "paid".equalsIgnoreCase(bill.getPaymentStatus())
                || "success".equalsIgnoreCase(bill.getPaymentStatus())
                || "success".equalsIgnoreCase(bill.getGatewayStatus());
    }
}
