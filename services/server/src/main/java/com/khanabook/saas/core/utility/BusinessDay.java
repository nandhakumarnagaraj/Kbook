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

    /** Default grace buffer (in minutes) for table settlement, bill printing, and drawer reconciliation. */
    public static final int SETTLEMENT_BUFFER_MINUTES = 60;

    public static boolean isActive(LocalTime cutoff, Long effectiveFromMillis) {
        return effectiveFromMillis != null && !LocalTime.MIDNIGHT.equals(cutoff);
    }

    /**
     * The boundary a shop rolls its order numbering on when no cutoff is activated.
     * Returns null when there is no morning extension past midnight (i.e. rollover at calendar midnight).
     *
     * <p>A shop still trading late into the night has no meaningful day change at midnight: an
     * 18:00 to 02:10 service, or a service closing at 00:00 (midnight) or late evening (e.g. 23:30),
     * must keep one numbering series until it closes and settles.
     * Table settlement, final bill printing, and cashier reconciliation take time past closing,
     * so an automatic 60-minute settlement buffer is appended:
     * <ol>
     *   <li>Overnight shifts (closing &lt; opening, e.g. 18:00 &rarr; 02:10) or midnight closings (closing == 00:00):
     *       buffer extends into early morning (02:10 &rarr; 03:10; 00:00 &rarr; 01:00).</li>
     *   <li>Late-evening shifts (buffered &lt; closing, e.g. 23:30 &rarr; 00:30):
     *       adding the 60-minute buffer wraps past midnight into the next morning.</li>
     *   <li>Daytime shifts (closing + buffer &le; 23:59 or closing == 23:00 &rarr; buffered == 00:00):
     *       buffer does not extend past midnight into the morning, so returns null and order
     *       numbering restarts naturally at calendar midnight.</li>
     * </ol>
     *
     * <p>Note: A late-evening shop that reopens near midnight continues the previous series until
     * the buffer elapses, so late table payments and drawer reconciliation stay unified.
     * <p>Note: Server-side {@code BillSyncService.applyServerBusinessDate} retains existing business dates
     * for already-stamped bills, protecting past orders from retroactive shifts.
     *
     * <p>Identical to the Android {@code BusinessDate.closingBoundary}. If the two differ,
     * {@code applyServerBusinessDate} rewrites pushed dates one day apart from the till.
     */
    public static LocalTime closingBoundary(LocalTime opening, LocalTime closing) {
        if (opening == null || closing == null) {
            return null;
        }

        LocalTime buffered = closing.plusMinutes(SETTLEMENT_BUFFER_MINUTES);
        boolean isOvernight = closing.isBefore(opening) || LocalTime.MIDNIGHT.equals(closing);
        if (isOvernight) {
            return buffered;
        }

        // Late evening closing where adding the settlement buffer wraps past midnight into next morning
        if (buffered.isBefore(closing) && !LocalTime.MIDNIGHT.equals(buffered)) {
            return buffered;
        }

        return null;
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
        return businessDate(createdAtMillis, zone, cutoff, effectiveFromMillis, null, null);
    }

    /**
     * Business date ({@code YYYY-MM-DD}) of {@code createdAtMillis} in {@code zone}, with the
     * shop's opening and closing times so an overnight service rolls over when it closes.
     */
    public static String businessDate(long createdAtMillis, ZoneId zone, LocalTime cutoff,
                                      Long effectiveFromMillis, LocalTime opening,
                                      LocalTime closing) {
        var zoned = Instant.ofEpochMilli(createdAtMillis).atZone(zone);
        LocalDate calendarDate = zoned.toLocalDate();

        if (!isActive(cutoff, effectiveFromMillis)) {
            LocalTime boundary = closingBoundary(opening, closing);
            return boundary != null && zoned.toLocalTime().isBefore(boundary)
                    ? calendarDate.minusDays(1).toString()
                    : calendarDate.toString();
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
        return windowStart(date, zone, cutoff, effectiveFromMillis, null, null);
    }

    /**
     * First instant belonging to business day {@code date}, honouring the closing-time
     * boundary of an overnight service.
     */
    public static long windowStart(LocalDate date, ZoneId zone, LocalTime cutoff,
                                   Long effectiveFromMillis, LocalTime opening,
                                   LocalTime closing) {
        long midnight = date.atStartOfDay(zone).toInstant().toEpochMilli();
        if (!isActive(cutoff, effectiveFromMillis)) {
            LocalTime boundary = closingBoundary(opening, closing);
            return boundary == null
                    ? midnight
                    : date.atTime(boundary).atZone(zone).toInstant().toEpochMilli();
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
        return windowEnd(date, zone, cutoff, effectiveFromMillis, null, null);
    }

    /** Exclusive end of business day {@code date}, honouring the closing-time boundary. */
    public static long windowEnd(LocalDate date, ZoneId zone, LocalTime cutoff,
                                 Long effectiveFromMillis, LocalTime opening,
                                 LocalTime closing) {
        return windowStart(date.plusDays(1), zone, cutoff, effectiveFromMillis, opening, closing);
    }
}
