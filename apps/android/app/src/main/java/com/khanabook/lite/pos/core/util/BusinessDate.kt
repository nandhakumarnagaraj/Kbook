package com.khanabook.lite.pos.core.util

import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The single rule that maps an instant to a business day.
 *
 * This exists because the day was previously derived in six places on this device
 * (`BillingViewModel` x3, both `RestaurantDao` counter transactions, `OrderIdManager`)
 * and again on the server. With a configurable cutoff every one of them must agree, or
 * the server's `BillSyncService.applyServerBusinessDate` silently rewrites the date on
 * the first push and the local duplicate guard starts looking in the wrong day.
 *
 * The rule, in order:
 *
 *  1. No cutoff configured (or a 00:00 cutoff)  -> the calendar day in the local zone.
 *     This branch is bit-identical to the previous behaviour, so deploying the schema
 *     changes nothing until a restaurant opts in.
 *  2. A cutoff is configured but the bill predates `cutoffEffectiveFrom`
 *     -> the calendar day. The change is forward-only: no stored business date moves.
 *  3. Otherwise -> the calendar day of `createdAt`, minus one when the local time of
 *     day is before the cutoff.
 *
 * `windowStart`/`windowEnd` are the inverse mapping, used by reports and by the
 * duplicate guard: business day D starts at D 00:00 before activation (and on the
 * switch day), and at D cutoff afterwards, which makes the switch day 28 hours long.
 * That is deliberate — see the design doc
 * `docs/planning/BUSINESS_DAY_CUTOFF_AND_SHIFTS_PLAN_2026-11-06.md`.
 */
object BusinessDate {

    const val DEFAULT_CUTOFF: String = "00:00"

    /**
     * Default grace buffer (in minutes) for table settlement, bill printing, and drawer
     * reconciliation after an overnight service closing time.
     */
    const val SETTLEMENT_BUFFER_MINUTES: Long = 60

    /** `HH:mm` or `HH:mm:ss`; anything unparseable falls back to midnight (legacy). */
    fun parseCutoff(raw: String?): LocalTime {
        if (raw.isNullOrBlank()) return LocalTime.MIDNIGHT
        val text = raw.trim()
        return try {
            LocalTime.parse(if (text.length == 5) "$text:00" else text)
        } catch (e: RuntimeException) {
            LocalTime.MIDNIGHT
        }
    }

    /**
     * The boundary a shop rolls its order numbering on when no cutoff is activated.
     *
     * A shop still trading at 01:00 has no meaningful day change at midnight: a 18:00 to
     * 02:10 service must keep one numbering series until it closes and settles.
     * Table settlement, final bill printing, and cashier reconciliation take time past closing,
     * so an automatic 60-minute settlement buffer is appended to closing time when the service
     * crosses midnight (closing earlier than opening).
     * A daytime shop (08:10 to 14:20) does not cross midnight, so it keeps the calendar day
     * and its reports keep natural dates.
     *
     * The Java twin is `BusinessDay.closingBoundary`; the two must stay identical or
     * `BillSyncService.applyServerBusinessDate` starts rewriting dates one day apart.
     */
    fun closingBoundary(openingTime: String?, closingTime: String?): LocalTime {
        val opening = parseClock(openingTime) ?: return LocalTime.MIDNIGHT
        val closing = parseClock(closingTime) ?: return LocalTime.MIDNIGHT
        if (closing == LocalTime.MIDNIGHT) return LocalTime.MIDNIGHT
        return if (closing < opening) closing.plusMinutes(SETTLEMENT_BUFFER_MINUTES) else LocalTime.MIDNIGHT
    }

    /** `HH:mm`/`HH:mm:ss` to [LocalTime], or null when absent or unparseable. */
    fun parseClock(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        return try {
            LocalTime.parse(if (text.length == 5) "$text:00" else text)
        } catch (e: RuntimeException) {
            null
        }
    }

    fun zoneOf(timezone: String?): ZoneId {
        return try {
            if (timezone.isNullOrBlank()) ZoneId.of(AppConstants.DEFAULT_TIMEZONE) else ZoneId.of(timezone)
        } catch (e: RuntimeException) {
            ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
        }
    }

    fun isCutoffActive(cutoff: LocalTime, effectiveFromMillis: Long?): Boolean {
        return effectiveFromMillis != null && cutoff != LocalTime.MIDNIGHT
    }

    /** Business date (`YYYY-MM-DD`) of [createdAtMillis] for this profile. */
    fun businessDate(createdAtMillis: Long, profile: RestaurantProfileEntity?): String {
        return businessDate(
            createdAtMillis = createdAtMillis,
            timezone = profile?.timezone,
            cutoffTime = profile?.businessDayCutoffTime,
            effectiveFromMillis = profile?.cutoffEffectiveFrom,
            openingTime = profile?.openingTime,
            closingTime = profile?.closingTime
        )
    }

    fun businessDate(
        createdAtMillis: Long,
        timezone: String?,
        cutoffTime: String?,
        effectiveFromMillis: Long?,
        openingTime: String? = null,
        closingTime: String? = null
    ): String {
        val zone = zoneOf(timezone)
        val zoned = Instant.ofEpochMilli(createdAtMillis).atZone(zone)
        val calendarDate = zoned.toLocalDate()
        val cutoff = parseCutoff(cutoffTime)

        if (!isCutoffActive(cutoff, effectiveFromMillis)) {
            // No activated cutoff: the shop's closing time is the boundary when the service
            // crosses midnight, otherwise the calendar day (unchanged legacy behaviour).
            val rollover = closingBoundary(openingTime, closingTime)
            return if (rollover != LocalTime.MIDNIGHT && zoned.toLocalTime() < rollover) {
                calendarDate.minusDays(1).toString()
            } else {
                calendarDate.toString()
            }
        }

        // Only the DATE of cutoff_effective_from matters: it names the first business day
        // under the new rule. Comparing the activation instant instead (t < effectiveFrom)
        // looks equivalent but is not — an activation between midnight and the cutoff puts
        // the same hours into two different days' windows, because those hours map backwards
        // to the previous day while the window helper still starts that day at midnight.
        // Keying on the date removes the case entirely, whichever time of day is stored.
        val effectiveDate = Instant.ofEpochMilli(effectiveFromMillis!!).atZone(zone).toLocalDate()
        if (calendarDate.isBefore(effectiveDate)) return calendarDate.toString()

        val businessDate = if (zoned.toLocalTime() < cutoff) calendarDate.minusDays(1) else calendarDate
        return businessDate.toString()
    }

    /** Today's business date for this profile. */
    fun today(profile: RestaurantProfileEntity?): String {
        return businessDate(System.currentTimeMillis(), profile)
    }

    /** First instant belonging to business day [date]. */    fun windowStart(
        date: LocalDate,
        profile: RestaurantProfileEntity?
    ): Long {
        return windowStart(
            date = date,
            timezone = profile?.timezone,
            cutoffTime = profile?.businessDayCutoffTime,
            effectiveFromMillis = profile?.cutoffEffectiveFrom,
            openingTime = profile?.openingTime,
            closingTime = profile?.closingTime
        )
    }

    fun windowStart(
        date: LocalDate,
        timezone: String?,
        cutoffTime: String?,
        effectiveFromMillis: Long?,
        openingTime: String? = null,
        closingTime: String? = null
    ): Long {
        val zone = zoneOf(timezone)
        val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val cutoff = parseCutoff(cutoffTime)

        if (!isCutoffActive(cutoff, effectiveFromMillis)) {
            // Exact inverse of businessDate(): an overnight day starts when the shop closes.
            val rollover = closingBoundary(openingTime, closingTime)
            return if (rollover == LocalTime.MIDNIGHT) {
                midnight
            } else {
                date.atTime(rollover).atZone(zone).toInstant().toEpochMilli()
            }
        }

        val effectiveDate = Instant.ofEpochMilli(effectiveFromMillis!!).atZone(zone).toLocalDate()
        // Days before the effective date still start at midnight, so hours already recorded
        // under the calendar rule are not orphaned — which makes the day BEFORE the
        // effective date 28 hours long. From the effective date on, every day starts at the
        // cutoff. `isBefore`, not `isAfter`: the effective date itself starts at the cutoff,
        // and that is what keeps this helper the exact inverse of businessDate().
        return if (date.isBefore(effectiveDate)) {
            midnight
        } else {
            date.atTime(cutoff).atZone(zone).toInstant().toEpochMilli()
        }
    }

    /** First instant belonging to the day after [date] — the exclusive end of [date]. */
    fun windowEnd(date: LocalDate, profile: RestaurantProfileEntity?): Long {
        return windowStart(date.plusDays(1), profile)
    }

    fun windowEnd(
        date: LocalDate,
        timezone: String?,
        cutoffTime: String?,
        effectiveFromMillis: Long?,
        openingTime: String? = null,
        closingTime: String? = null
    ): Long {
        return windowStart(date.plusDays(1), timezone, cutoffTime, effectiveFromMillis, openingTime, closingTime)
    }
}
