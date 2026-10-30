package com.khanabook.saas.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncPushMetricsTest {

    @Test
    void recordsAcceptedAndRejectedCounts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SyncPushMetrics metrics = new SyncPushMetrics(registry);

        metrics.recordPush(3, 2, Map.of(10L, "could not execute statement"));

        assertEquals(1.0, registry.get("sync.push.batch").counter().count(), 0.001);
        assertEquals(3.0,
                registry.get("sync.push.records").tag("outcome", "success").counter().count(), 0.001);
        assertEquals(2.0,
                registry.get("sync.push.records").tag("outcome", "failed").counter().count(), 0.001);
        assertEquals(1.0,
                registry.get("sync.push.failed").tag("reason", SyncFailureCategory.OTHER).counter().count(), 0.001);
    }

    @Test
    void recordsOneFailureSamplePerRejectedRecord() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SyncPushMetrics metrics = new SyncPushMetrics(registry);

        Map<Long, String> reasons = new LinkedHashMap<>();
        reasons.put(1L, "Bill belongs to another terminal and cannot be modified from this terminal");
        reasons.put(2L, "STALE_PUSH_CONFLICT: another device updated this row");
        reasons.put(3L, "STALE_PUSH_CONFLICT: another device updated this row");

        metrics.recordPush(0, 3, reasons);

        assertEquals(1.0, registry.get("sync.push.failed")
                .tag("reason", SyncFailureCategory.CROSS_TERMINAL).counter().count(), 0.001);
        assertEquals(2.0, registry.get("sync.push.failed")
                .tag("reason", SyncFailureCategory.STALE_PUSH_CONFLICT).counter().count(), 0.001);
        assertEquals(0.0, registry.get("sync.push.records")
                .tag("outcome", "success").counter().count(), 0.001);
    }

    @Test
    void allSuccessDoesNotRegisterAFailedSeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SyncPushMetrics metrics = new SyncPushMetrics(registry);

        metrics.recordPush(5, 0, Map.of());

        assertEquals(5.0,
                registry.get("sync.push.records").tag("outcome", "success").counter().count(), 0.001);
        assertEquals(0, registry.find("sync.push.records").tag("outcome", "failed").counters().size());
    }
}
