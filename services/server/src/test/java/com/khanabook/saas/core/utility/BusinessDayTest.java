package com.khanabook.saas.core.utility;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary table shared with the Android {@code BusinessDateTest}. A client and a server
 * that disagree by one day is the failure this rule exists to prevent.
 */
class BusinessDayTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static long at(int year, int month, int day, int hour, int minute) {
        return java.time.LocalDateTime.of(year, month, day, hour, minute)
                .atZone(IST).toInstant().toEpochMilli();
    }

    private static final long ACTIVATED_AT = at(2026, 11, 10, 0, 0);
    private static final LocalTime FOUR_AM = LocalTime.of(4, 0);

    @Test
    void noCutoffKeepsTheCalendarDay() {
        assertThat(BusinessDay.businessDate(at(2026, 11, 6, 1, 30), IST, LocalTime.MIDNIGHT, null))
                .isEqualTo("2026-11-06");
        assertThat(BusinessDay.businessDate(at(2026, 11, 6, 4, 30), IST, LocalTime.MIDNIGHT, null))
                .isEqualTo("2026-11-06");
    }

    @Test
    void midnightCutoffIsTheLegacyRuleEvenWhenAnEffectiveInstantIsSet() {
        assertThat(BusinessDay.businessDate(at(2026, 11, 6, 1, 30), IST, LocalTime.MIDNIGHT, ACTIVATED_AT))
                .isEqualTo("2026-11-06");
    }

    @Test
    void billCreatedBeforeActivationKeepsTheCalendarDay() {
        assertThat(BusinessDay.businessDate(at(2026, 11, 9, 1, 30), IST, FOUR_AM, ACTIVATED_AT))
                .isEqualTo("2026-11-09");
    }

    @Test
    void billBeforeTheCutoffBelongsToThePreviousBusinessDay() {
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 3, 59), IST, FOUR_AM, ACTIVATED_AT))
                .isEqualTo("2026-11-09");
    }

    @Test
    void theCutoffInstantItselfBelongsToTheNewBusinessDay() {
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 4, 0), IST, FOUR_AM, ACTIVATED_AT))
                .isEqualTo("2026-11-10");
    }

    @Test
    void justAfterTheCutoffBelongsToTheNewBusinessDay() {
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 4, 1), IST, FOUR_AM, ACTIVATED_AT))
                .isEqualTo("2026-11-10");
    }

    @Test
    void lateEveningStaysOnTheSameBusinessDay() {
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 23, 0), IST, FOUR_AM, ACTIVATED_AT))
                .isEqualTo("2026-11-10");
    }

    @Test
    void unparseableCutoffFallsBackToTheLegacyRule() {
        assertThat(BusinessDay.parseCutoff("not-a-time")).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(BusinessDay.businessDate(at(2026, 11, 6, 1, 30), IST,
                BusinessDay.parseCutoff("not-a-time"), ACTIVATED_AT))
                .isEqualTo("2026-11-06");
    }

    @Test
    void theRestaurantTimezoneDecidesTheCalendarDay() {
        ZoneId newYork = ZoneId.of("America/New_York");
        long instant = Instant.parse("2026-09-12T03:30:00Z").toEpochMilli();
        assertThat(BusinessDay.businessDate(instant, newYork, LocalTime.MIDNIGHT, null))
                .isEqualTo("2026-09-11");
    }

    @Test
    void invalidTimezoneFallsBackToIst() {
        long instant = Instant.parse("2026-09-10T18:39:00Z").toEpochMilli();
        assertThat(BusinessDay.businessDate(instant, BusinessDay.zoneOf("Not/AZone"),
                LocalTime.MIDNIGHT, null))
                .isEqualTo("2026-09-11");
    }

    @Test
    void theDayBeforeTheEffectiveDateIsTheTwentyEightHourOne() {
        LocalDate lastCalendarDay = LocalDate.of(2026, 11, 9);
        assertThat(BusinessDay.windowStart(lastCalendarDay, IST, FOUR_AM, ACTIVATED_AT))
                .isEqualTo(at(2026, 11, 9, 0, 0));
        assertThat(BusinessDay.windowEnd(lastCalendarDay, IST, FOUR_AM, ACTIVATED_AT))
                .isEqualTo(at(2026, 11, 10, 4, 0));
    }

    @Test
    void theEffectiveDateItselfStartsAtTheCutoff() {
        LocalDate effectiveDate = LocalDate.of(2026, 11, 10);
        assertThat(BusinessDay.windowStart(effectiveDate, IST, FOUR_AM, ACTIVATED_AT))
                .isEqualTo(at(2026, 11, 10, 4, 0));
        assertThat(BusinessDay.windowEnd(effectiveDate, IST, FOUR_AM, ACTIVATED_AT))
                .isEqualTo(at(2026, 11, 11, 4, 0));
    }

    @Test
    void everyInstantInAWindowMapsBackToThatBusinessDate() {
        // The failure this guards: businessDate() and windowStart()/windowEnd() disagreeing
        // by one day, which double-counts four hours in reports and makes the server's
        // duplicate-order key look in a different day from the client's guard.
        for (int day = 7; day <= 13; day++) {
            LocalDate date = LocalDate.of(2026, 11, day);
            long start = BusinessDay.windowStart(date, IST, FOUR_AM, ACTIVATED_AT);
            long end = BusinessDay.windowEnd(date, IST, FOUR_AM, ACTIVATED_AT);
            assertThat(end).isGreaterThan(start);

            for (long instant = start; instant < end; instant += 15 * 60 * 1000L) {
                assertThat(BusinessDay.businessDate(instant, IST, FOUR_AM, ACTIVATED_AT))
                        .as("instant %s should belong to %s", instant, date)
                        .isEqualTo(date.toString());
            }
            assertThat(BusinessDay.businessDate(start - 1, IST, FOUR_AM, ACTIVATED_AT))
                    .isNotEqualTo(date.toString());
        }
    }

    @Test
    void onlyTheDateOfTheEffectiveInstantMatters() {
        long midnightActivation = at(2026, 11, 10, 0, 0);
        long midDayActivation = at(2026, 11, 10, 15, 0);
        LocalDate effectiveDate = LocalDate.of(2026, 11, 10);

        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 1, 0), IST, FOUR_AM, midnightActivation))
                .isEqualTo("2026-11-09");
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 1, 0), IST, FOUR_AM, midDayActivation))
                .isEqualTo("2026-11-09");
        assertThat(BusinessDay.windowStart(effectiveDate, IST, FOUR_AM, midnightActivation))
                .isEqualTo(BusinessDay.windowStart(effectiveDate, IST, FOUR_AM, midDayActivation));
    }

    @Test
    void overnightServiceRollsOverAtClosingTimeNotMidnight() {
        LocalTime open = LocalTime.of(18, 0);
        LocalTime close = LocalTime.of(2, 10);

        // 01:00 is still the previous night's service.
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 1, 0), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        // 02:10 is closing, but 60m settlement buffer (until 03:10) keeps table settlements on previous day.
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 2, 10), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 3, 9), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        // The settlement boundary (03:10) starts the next series.
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 3, 10), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-10");
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 23, 0), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-10");
    }

    @Test
    void midnightClosingServiceGets60mBufferUntil01_00AM() {
        LocalTime open = LocalTime.of(12, 0);
        LocalTime close = LocalTime.MIDNIGHT;

        // 23:45 is on the previous day.
        assertThat(BusinessDay.businessDate(at(2026, 11, 9, 23, 45), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        // 00:30 AM is within 60m settlement buffer (until 01:00 AM) and belongs to Nov 09.
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 0, 30), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 0, 59), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        // At 01:00 AM, the shift rolls over to Nov 10.
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 1, 0), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-10");
    }

    @Test
    void lateEvening23_30ClosingGets60mBufferUntil00_30AM() {
        LocalTime open = LocalTime.of(11, 0);
        LocalTime close = LocalTime.of(23, 30);

        // 23:45 is on Nov 09.
        assertThat(BusinessDay.businessDate(at(2026, 11, 9, 23, 45), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        // 00:15 AM is within 60m buffer (until 00:30 AM) and belongs to Nov 09.
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 0, 15), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        // At 00:30 AM, rolls over to Nov 10.
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 0, 30), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-10");
    }

    @Test
    void default23_00ClosingServiceRollsOverAtMidnight() {
        LocalTime open = LocalTime.of(8, 0);
        LocalTime close = LocalTime.of(23, 0);

        // 23:59 is on Nov 09.
        assertThat(BusinessDay.businessDate(at(2026, 11, 9, 23, 59), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        // 00:00 midnight rolls over to Nov 10.
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 0, 0), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-10");
    }

    @Test
    void lateEveningWrapAppliesEvenWhenOpeningIsNearMidnight() {
        LocalTime open = LocalTime.of(0, 10);
        LocalTime close = LocalTime.of(23, 50);

        // 23:50 + 60m buffer = 00:50 AM boundary.
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 0, 45), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 0, 50), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-10");
    }

    @Test
    void daytimeServiceKeepsTheCalendarDay() {
        LocalTime open = LocalTime.of(8, 10);
        LocalTime close = LocalTime.of(14, 20);

        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 1, 0), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-10");
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 15, 0), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-10");
    }

    @Test
    void activatedCutoffStillWinsOverTheClosingTime() {
        LocalTime open = LocalTime.of(18, 0);
        LocalTime close = LocalTime.of(2, 10);

        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 3, 59), IST, FOUR_AM, ACTIVATED_AT, open, close))
                .isEqualTo("2026-11-09");
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 4, 0), IST, FOUR_AM, ACTIVATED_AT, open, close))
                .isEqualTo("2026-11-10");
    }

    @Test
    void overnightWindowsAreTheExactInverseOfTheClosingBoundary() {
        LocalTime open = LocalTime.of(18, 0);
        LocalTime close = LocalTime.of(2, 10);
        LocalDate day = LocalDate.of(2026, 11, 10);

        // With 60m settlement buffer, boundary is 03:10
        assertThat(BusinessDay.windowStart(day, IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo(at(2026, 11, 10, 3, 10));
        assertThat(BusinessDay.windowEnd(day, IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo(at(2026, 11, 11, 3, 10));
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 3, 10), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-10");
        assertThat(BusinessDay.businessDate(at(2026, 11, 10, 3, 9), IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo("2026-11-09");
    }

    @Test
    void midnightClosingWindowsAreInverseOfThe01_00AMBoundary() {
        LocalTime open = LocalTime.of(12, 0);
        LocalTime close = LocalTime.MIDNIGHT;
        LocalDate day = LocalDate.of(2026, 11, 10);
        assertThat(BusinessDay.windowStart(day, IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo(at(2026, 11, 10, 1, 0));
        assertThat(BusinessDay.windowEnd(day, IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo(at(2026, 11, 11, 1, 0));
    }

    @Test
    void lateEveningClosingWindowsAreInverseOfThe00_30AMBoundary() {
        LocalTime open = LocalTime.of(11, 0);
        LocalTime close = LocalTime.of(23, 30);
        LocalDate day = LocalDate.of(2026, 11, 10);
        assertThat(BusinessDay.windowStart(day, IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo(at(2026, 11, 10, 0, 30));
        assertThat(BusinessDay.windowEnd(day, IST, LocalTime.MIDNIGHT, null, open, close))
                .isEqualTo(at(2026, 11, 11, 0, 30));
    }

    @Test
    void legacyWindowsStayMidnightToMidnight() {
        LocalDate day = LocalDate.of(2026, 11, 6);
        assertThat(BusinessDay.windowStart(day, IST, LocalTime.MIDNIGHT, null))
                .isEqualTo(at(2026, 11, 6, 0, 0));
        assertThat(BusinessDay.windowEnd(day, IST, LocalTime.MIDNIGHT, null))
                .isEqualTo(at(2026, 11, 7, 0, 0));
    }

    @Test
    void closingBoundaryReturnsNullWhenNoMorningExtensionExists() {
        // Default 23:00 has buffer ending at midnight -> null (calendar midnight rollover)
        assertThat(BusinessDay.closingBoundary(LocalTime.of(8, 0), LocalTime.of(23, 0))).isNull();
        // Daytime closing -> null
        assertThat(BusinessDay.closingBoundary(LocalTime.of(8, 0), LocalTime.of(14, 0))).isNull();
        // Overnight closing 02:10 -> 03:10
        assertThat(BusinessDay.closingBoundary(LocalTime.of(18, 0), LocalTime.of(2, 10)))
                .isEqualTo(LocalTime.of(3, 10));
        // Midnight closing 00:00 -> 01:00
        assertThat(BusinessDay.closingBoundary(LocalTime.of(12, 0), LocalTime.MIDNIGHT))
                .isEqualTo(LocalTime.of(1, 0));
        // Late evening 23:30 -> 00:30
        assertThat(BusinessDay.closingBoundary(LocalTime.of(11, 0), LocalTime.of(23, 30)))
                .isEqualTo(LocalTime.of(0, 30));
    }
}
