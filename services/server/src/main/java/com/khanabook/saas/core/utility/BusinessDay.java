package com.khanabook.saas.core.utility;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Server twin of the Android {@code BusinessDate} rule.
 *
 * <p>{@code BillSyncService.applyServerBusinessDate} is the single authority for a bill's
 * business day: it overwrites whatever the device computed on every push. With a
 * configurable cutoff the client and this class must produce the same answer for the same
 * instant, or the server's duplicate key ({@code lastResetDate}) and the device's local
 * guard start looking in different days for four hours a night. Both implementations are
 * covered by the same boundary table — see the design doc
 * {@code docs/planning/BUSINESS_DAY_CUTOFF_AND_SHIFTS_PLAN_2026-11-06.md} §10.
 *
 * <p>The rule, in order:
 * <ol>
 *   <li>No cutoff configured (or a 00:00 cutoff) — the calendar day in the profile zone.
 *       Bit-identical to the previous behaviour, so deploying the schema changes nothing.</li>
 *   <li>A cutoff is configured but the bill predates {@code cutoff_effective_from} — the
 *       calendar day. Forward-only: no stored business date is rewritten.</li>
 *   <li>Otherwise — the calendar day, minus one when the local time of day is before the
 *       cutoff.</li>
 * </ol>
 */
public final class BusinessDay {

    private BusinessDay() {
    }

    /** {@code HH:mm} or {@code HH:mm:ss}; anything unparseable falls back to midnight. */
    public static LocalTime parseCutoff(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        String text = raw.trim();
        try {
            return LocalTime.parse(text.length() == 5 ? text + ":00" : text);
        } catch (RuntimeException ignored) {
            return LocalTime.MIDNIGHT;
        }
    }

    public static boolean isActive(LocalTime cutoff, Long effectiveFromMillis) {
        return effectiveFromMillis != null && !LocalTime.MIDNIGHT.equals(cutoff);
    }

    public static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of(AppConstants.DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException ignored) {
            return ZoneId.of(AppConstants.DEFAULT_TIMEZONE);
        }
    }

    /** Business date ({@code YYYY-MM-DD}) of {@code createdAtMillis} in {@code zone}. */
    public static String businessDate(long createdAtMillis, ZoneId zone, LocalTime cutoff,
                                      Long effectiveFromMillis) {
        var zoned = Instant.ofEpochMilli(createdAtMillis).atZone(zone);
        LocalDate calendarDate = zoned.toLocalDate();

        if (!isActive(cutoff, effectiveFromMillis)) {
            return calendarDate.toString();
        }

        // Only the DATE of cutoff_effective_from matters: it names the first business day
        // under the new rule. Comparing the activation instant instead is not equivalent —
        // an activation between midnight and the cutoff puts the same hours into two
        // different days' windows, because those hours map backwards to the previous day
        // while the window helper still starts that day at midnight.
        LocalDate effectiveDate = Instant.ofEpochMilli(effectiveFromMillis.longValue())
                .atZone(zone)
                .toLocalDate();
        if (calendarDate.isBefore(effectiveDate)) {
            return calendarDate.toString();
        }

        LocalDate businessDate = zoned.toLocalTime().isBefore(cutoff)
                ? calendarDate.minusDays(1)
                : calendarDate;
        return businessDate.toString();
    }

    /**
     * First instant belonging to business day {@code date}.
     *
     * <p>The switch day (and every day before it) still starts at midnight, so the hours
     * already recorded under the calendar rule are not orphaned; that makes the switch day
     * 28 hours long. Every day after it starts at the cutoff.
     */
    public static long windowStart(LocalDate date, ZoneId zone, LocalTime cutoff,
                                   Long effectiveFromMillis) {
        long midnight = date.atStartOfDay(zone).toInstant().toEpochMilli();
        if (!isActive(cutoff, effectiveFromMillis)) {
            return midnight;
        }
        LocalDate effectiveDate = Instant.ofEpochMilli(effectiveFromMillis.longValue())
                .atZone(zone)
                .toLocalDate();
        // Days before the effective date still start at midnight, so hours already recorded
        // under the calendar rule are not orphaned — which makes the day BEFORE the
        // effective date 28 hours long. From the effective date on, every day starts at the
        // cutoff. `isBefore`, not `isAfter`: the effective date itself starts at the cutoff,
        // and that is what keeps this helper the exact inverse of businessDate().
        if (date.isBefore(effectiveDate)) {
            return midnight;
        }
        return date.atTime(cutoff).atZone(zone).toInstant().toEpochMilli();
    }

    /** Exclusive end of business day {@code date} — the start of the following day. */
    public static long windowEnd(LocalDate date, ZoneId zone, LocalTime cutoff,
                                 Long effectiveFromMillis) {
        return windowStart(date.plusDays(1), zone, cutoff, effectiveFromMillis);
    }
}
