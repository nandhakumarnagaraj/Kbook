package com.khanabook.saas.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebhookMetricsTest {

    @Test
    void eachReasonIsCountedIndependently() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebhookMetrics metrics = new WebhookMetrics(registry);

        metrics.dropped(WebhookMetrics.REASON_AMOUNT_MISMATCH);
        metrics.dropped(WebhookMetrics.REASON_DUPLICATE);
        metrics.dropped(WebhookMetrics.REASON_DUPLICATE);
        metrics.dropped(WebhookMetrics.REASON_ALREADY_PAID);
        metrics.dropped(WebhookMetrics.REASON_HASH_MISMATCH);

        assertEquals(1.0, registry.get("payments.webhook.dropped")
                .tag("reason", WebhookMetrics.REASON_AMOUNT_MISMATCH).counter().count(), 0.001);
        assertEquals(2.0, registry.get("payments.webhook.dropped")
                .tag("reason", WebhookMetrics.REASON_DUPLICATE).counter().count(), 0.001);
        assertEquals(1.0, registry.get("payments.webhook.dropped")
                .tag("reason", WebhookMetrics.REASON_ALREADY_PAID).counter().count(), 0.001);
        assertEquals(1.0, registry.get("payments.webhook.dropped")
                .tag("reason", WebhookMetrics.REASON_HASH_MISMATCH).counter().count(), 0.001);
    }

    @Test
    void recordingNeverThrows() {
        // A closed registry stands in for a metrics-backend failure; the webhook ack
        // path must survive it.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.close();
        WebhookMetrics metrics = new WebhookMetrics(registry);

        metrics.dropped(WebhookMetrics.REASON_AMOUNT_MISMATCH);
    }
}
