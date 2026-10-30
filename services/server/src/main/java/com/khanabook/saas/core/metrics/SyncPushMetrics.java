package com.khanabook.saas.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Money-path counters for sync push.
 *
 * <p>Extracted from {@code GenericSyncService} so the recording can be unit-tested
 * with a {@code SimpleMeterRegistry} without standing up the whole service (which
 * needs ~20 collaborators). The service holds this as an optional dependency: a
 * missing registry disables metrics, it never fails a sync.
 *
 * <p>Recording is strictly best-effort. Every method swallows its own failures —
 * a metrics backend problem must not turn into a rejected bill.
 */
@Component
public class SyncPushMetrics {

    private final MeterRegistry meterRegistry;

    public SyncPushMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordBatch() {
        try {
            Counter.builder("sync.push.batch")
                    .description("Push sync entity batches accepted by the server")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    public void recordRecords(int success, int failed) {
        try {
            Counter.builder("sync.push.records")
                    .description("Pushed records by outcome")
                    .tag("outcome", "success")
                    .register(meterRegistry)
                    .increment(success);
            if (failed > 0) {
                Counter.builder("sync.push.records")
                        .description("Pushed records by outcome")
                        .tag("outcome", "failed")
                        .register(meterRegistry)
                        .increment(failed);
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    public void recordFailure(String reason) {
        try {
            Counter.builder("sync.push.failed")
                    .description("Rejected pushed records by reason category")
                    .tag("reason", SyncFailureCategory.categorize(reason))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    /** One entry point for the push path: counts the batch, both outcomes and reasons. */
    public void recordPush(int success, int failed, Map<Long, String> failedReasons) {
        recordBatch();
        recordRecords(success, failed);
        if (failedReasons != null) {
            failedReasons.values().forEach(this::recordFailure);
        }
    }
}
