package com.khanabook.saas.core.metrics;

import java.util.Locale;

/**
 * Maps a sync rejection message onto a small, bounded Prometheus label value.
 *
 * <p>Why not tag with the message: the reason strings are human sentences with
 * counts, ids and tokens in them. Tagging with them would create unbounded label
 * cardinality and kill the scrape. Why not parse the leading ALL_CAPS token only:
 * most reasons are sentences ("Bill belongs to another terminal…"), so almost
 * everything collapsed into {@code OTHER} and the alert could not distinguish a
 * clock-skew storm from a permission rejection.
 *
 * <p>This classifier is deliberately a lookup table, not a heuristic: every entry
 * below is a reason string that the push path actually writes. When you add a new
 * {@code failedReasons.put(...)} sentence, add its category here and cover it in
 * {@code SyncFailureCategoryTest}.
 */
public final class SyncFailureCategory {

    public static final String CROSS_TERMINAL = "CROSS_TERMINAL";
    public static final String CLOCK_SKEW = "CLOCK_SKEW";
    public static final String STALE_RECORD = "STALE_RECORD";
    public static final String STALE_PUSH_CONFLICT = "STALE_PUSH_CONFLICT";
    public static final String STALE_STATUS_PUSH = "STALE_STATUS_PUSH";
    public static final String ROLE_ESCALATION = "ROLE_ESCALATION";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String OTHER = "OTHER";

    private SyncFailureCategory() {}

    public static String categorize(String reason) {
        if (reason == null || reason.isBlank()) return OTHER;
        String r = reason.trim();
        String upper = r.toUpperCase(Locale.ROOT);

        // Explicit reason codes written by the push path (prefix form).
        if (upper.startsWith("STALE_PUSH_CONFLICT")) return STALE_PUSH_CONFLICT;
        if (upper.startsWith("STALE_STATUS_PUSH")) return STALE_STATUS_PUSH;

        // Sentence-form reasons written by the push path.
        if (r.contains("another terminal")) return CROSS_TERMINAL;
        if (r.startsWith("Terminal clock is")) return CLOCK_SKEW;
        if (r.startsWith("Incoming record is older than the server record")) return STALE_RECORD;
        if (r.contains("owner may create staff accounts")) return ROLE_ESCALATION;
        if (upper.contains("OUT_OF_STOCK") || r.startsWith("Insufficient stock")) return OUT_OF_STOCK;
        if (r.startsWith("Legacy bill must be reassigned")) return VALIDATION_ERROR;
        if (r.startsWith("Sync rejected by server")) return VALIDATION_ERROR;

        // Last resort: a leading ALL_CAPS token (some call sites pass codes).
        String head = r.split("\\s*[:\\-]\\s*", 2)[0];
        if (!head.isEmpty() && head.length() <= 40
                && head.equals(head.toUpperCase(Locale.ROOT))
                && head.chars().anyMatch(Character::isLetter)) {
            return head.replace(' ', '_');
        }
        return OTHER;
    }
}
