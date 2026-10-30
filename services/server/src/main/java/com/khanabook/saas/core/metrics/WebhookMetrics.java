package com.khanabook.saas.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Webhook outcome counters.
 *
 * <p>{@link #dropped(String)} means "a webhook arrived but did not settle a bill".
 * The reasons are a fixed vocabulary, so cardinality is bounded by the code, not
 * by the payload:
 * <ul>
 *   <li>{@code hash_mismatch} — signature failed; a config/key problem, or a
 *       spoofed request. Not proof of a customer-facing failure on its own.</li>
 *   <li>{@code amount_mismatch} — gateway amount differs from the bill; real
 *       money risk, the bill stays unpaid.</li>
 *   <li>{@code duplicate} — the same (txnid, status) already processed. Healthy.</li>
 *   <li>{@code already_paid} — bill was already settled by another path (manual
 *       verify or a different txnid). Healthy idempotent no-op.</li>
 * </ul>
 *
 * <p>The distinction matters: only {@code amount_mismatch} (and a non-duplicate
 * hash mismatch sustained over time) should page a human. Alerting on all of them
 * produced false criticals for replay traffic.
 */
@Component
public class WebhookMetrics {

    public static final String REASON_HASH_MISMATCH = "hash_mismatch";
    public static final String REASON_AMOUNT_MISMATCH = "amount_mismatch";
    public static final String REASON_DUPLICATE = "duplicate";
    public static final String REASON_ALREADY_PAID = "already_paid";

    private final MeterRegistry meterRegistry;

    public WebhookMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void dropped(String reason) {
        try {
            Counter.builder("payments.webhook.dropped")
                    .description("Easebuzz webhooks received but not applied to a bill")
                    .tag("reason", reason)
                    .register(meterRegistry)
                    .increment();
        } catch (Exception ignored) {
            // metrics are best-effort; never fail a webhook ack
        }
    }
}
