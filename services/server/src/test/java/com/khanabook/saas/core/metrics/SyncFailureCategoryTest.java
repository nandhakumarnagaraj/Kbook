package com.khanabook.saas.core.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks the mapping between the reason strings the push path actually writes and
 * the bounded Prometheus label values. Every new {@code failedReasons.put(...)}
 * sentence needs an entry here — the metric is only as useful as this table.
 */
class SyncFailureCategoryTest {

    @Test
    void explicitReasonCodesMapToThemselves() {
        assertEquals(SyncFailureCategory.STALE_PUSH_CONFLICT,
                SyncFailureCategory.categorize(
                        "STALE_PUSH_CONFLICT: another device updated this Category since your last sync. Pull to see the latest version."));
        assertEquals(SyncFailureCategory.STALE_STATUS_PUSH,
                SyncFailureCategory.categorize(
                        "STALE_STATUS_PUSH: status edit was rejected because a newer state is already recorded (device statusVersion 3 vs server 5)."));
    }

    @Test
    void sentenceReasonsAreNotCollapsedIntoOther() {
        assertEquals(SyncFailureCategory.CROSS_TERMINAL,
                SyncFailureCategory.categorize(
                        "Bill belongs to another terminal and cannot be modified from this terminal"));
        assertEquals(SyncFailureCategory.CROSS_TERMINAL,
                SyncFailureCategory.categorize(
                        "Record references a bill owned by another terminal"));
        assertEquals(SyncFailureCategory.CLOCK_SKEW,
                SyncFailureCategory.categorize(
                        "Terminal clock is behind by 900 seconds. Please correct time settings on the device."));
        assertEquals(SyncFailureCategory.STALE_RECORD,
                SyncFailureCategory.categorize(
                        "Incoming record is older than the server record"));
        assertEquals(SyncFailureCategory.ROLE_ESCALATION,
                SyncFailureCategory.categorize(
                        "Only the restaurant owner may create staff accounts"));
        assertEquals(SyncFailureCategory.VALIDATION_ERROR,
                SyncFailureCategory.categorize(
                        "Legacy bill must be reassigned by admin before modification"));
    }

    @Test
    void unknownAndBlankReasonsFallBackToOther() {
        assertEquals(SyncFailureCategory.OTHER, SyncFailureCategory.categorize(null));
        assertEquals(SyncFailureCategory.OTHER, SyncFailureCategory.categorize("   "));
        assertEquals(SyncFailureCategory.OTHER,
                SyncFailureCategory.categorize("could not execute statement"));
    }

    @Test
    void leadingUpperSnakeTokenIsKept() {
        assertEquals("SOME_NEW_CODE",
                SyncFailureCategory.categorize("SOME_NEW_CODE: detail text"));
    }
}
