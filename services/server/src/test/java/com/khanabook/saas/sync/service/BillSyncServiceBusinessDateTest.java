package com.khanabook.saas.sync.service;

import com.khanabook.saas.feature.billing.service.BillSyncService;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The business day of a bill must be derived server-side from {@code createdAt} in the
 * restaurant's timezone.
 *
 * <p>Regression cover for the duplicate-order sync loop: the server used to fill a blank
 * {@code lastResetDate} with {@code LocalDate.now()} (no zone, so UTC in the container).
 * For a bill created just after midnight IST that yields the PREVIOUS day, so the server's
 * duplicate key (lastResetDate string) disagreed with the client's guard (createdAt IST
 * window) and pushes were rejected as "Duplicate order #X already exists".
 */
@ExtendWith(MockitoExtension.class)
class BillSyncServiceBusinessDateTest {

    private static final long TENANT = 4912877311365041681L;

    @Mock private BillRepository billRepository;
    @Mock private RestaurantProfileRepository restaurantProfileRepository;

    private BillSyncService service() {
        return new BillSyncService(billRepository, restaurantProfileRepository);
    }

    private void profileWithTimezone(String timezone) {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setTimezone(timezone);
        when(restaurantProfileRepository.findByRestaurantId(TENANT)).thenReturn(Optional.of(profile));
    }

    private void profileWithCutoff(String timezone, java.time.LocalTime cutoff, Long effectiveFrom) {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setTimezone(timezone);
        profile.setBusinessDayCutoffTime(cutoff);
        profile.setCutoffEffectiveFrom(effectiveFrom);
        when(restaurantProfileRepository.findByRestaurantId(TENANT)).thenReturn(Optional.of(profile));
    }

    private Bill billCreatedAt(String isoInstant, String clientSuppliedDate) {
        Bill bill = new Bill();
        bill.setLocalId(112L);
        bill.setCreatedAt(Instant.parse(isoInstant).toEpochMilli());
        bill.setLastResetDate(clientSuppliedDate);
        return bill;
    }

    @Test
    void justAfterMidnightIst_belongsToTheIstDay_notTheUtcDay() {
        profileWithTimezone("Asia/Kolkata");
        // 2026-09-10T18:39Z == 2026-09-11 00:09 IST. UTC would say the 10th.
        Bill bill = billCreatedAt("2026-09-10T18:39:00Z", null);

        service().applyServerBusinessDate(TENANT, bill);

        assertThat(bill.getLastResetDate()).isEqualTo("2026-09-11");
    }

    @Test
    void lateEveningIst_staysOnTheSameIstDay() {
        profileWithTimezone("Asia/Kolkata");
        // 2026-09-12T17:53Z == 2026-09-12 23:23 IST — same day in both zones.
        Bill bill = billCreatedAt("2026-09-12T17:53:00Z", null);

        service().applyServerBusinessDate(TENANT, bill);

        assertThat(bill.getLastResetDate()).isEqualTo("2026-09-12");
    }

    @Test
    void clientSuppliedDateIsOverwritten_soOneSideOnlyDecidesTheDay() {
        profileWithTimezone("Asia/Kolkata");
        // A device with a skewed clock claims tomorrow; the server must correct it.
        Bill bill = billCreatedAt("2026-09-12T05:53:00Z", "2026-09-13");

        service().applyServerBusinessDate(TENANT, bill);

        assertThat(bill.getLastResetDate()).isEqualTo("2026-09-12");
    }

    @Test
    void honoursANonIndianRestaurantTimezone() {
        profileWithTimezone("America/New_York");
        // 2026-09-12T03:30Z == 2026-09-11 23:30 in New York.
        Bill bill = billCreatedAt("2026-09-12T03:30:00Z", null);

        service().applyServerBusinessDate(TENANT, bill);

        assertThat(bill.getLastResetDate()).isEqualTo("2026-09-11");
    }

    @Test
    void blankOrInvalidTimezoneFallsBackToTheDefault() {
        profileWithTimezone("Not/AZone");
        Bill bill = billCreatedAt("2026-09-10T18:39:00Z", null);

        service().applyServerBusinessDate(TENANT, bill);

        // Default is Asia/Kolkata, so the IST day still wins.
        assertThat(bill.getLastResetDate()).isEqualTo("2026-09-11");
    }

    @Test
    void missingCreatedAtLeavesTheBillUntouched() {
        Bill bill = new Bill();
        bill.setLastResetDate("2026-09-12");

        service().applyServerBusinessDate(TENANT, bill);

        assertThat(bill.getLastResetDate()).isEqualTo("2026-09-12");
    }

    @Test
    void aDateTheServerAlreadyStampedIsNeverMoved() {
        // 2026-11-11T20:00Z == 2026-11-12 01:30 IST, so a recompute would say the 12th.
        // The bill was already numbered under the 11th; moving it would collide with an
        // occupied daily order slot and be repaired into a new number on a printed receipt.
        Bill bill = billCreatedAt("2026-11-11T20:00:00Z", "2026-11-11");

        service().applyServerBusinessDate(TENANT, bill, "2026-11-11");

        assertThat(bill.getLastResetDate()).isEqualTo("2026-11-11");
    }

    @Test
    void aCutoffProfileMovesEarlyMorningBillsToThePreviousDay() {
        // Cutoff takes effect on the 10th; 01:30 on the 10th is still the 9th's business.
        long effectiveFrom = java.time.LocalDateTime.of(2026, 11, 10, 0, 0)
                .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        profileWithCutoff("Asia/Kolkata", java.time.LocalTime.of(4, 0), effectiveFrom);
        Bill bill = billCreatedAt("2026-11-09T20:00:00Z", null); // 2026-11-10 01:30 IST

        service().applyServerBusinessDate(TENANT, bill);

        assertThat(bill.getLastResetDate()).isEqualTo("2026-11-09");
    }

    @Test
    void aCutoffProfileLeavesTheRestOfTheDayAlone() {
        long effectiveFrom = java.time.LocalDateTime.of(2026, 11, 10, 0, 0)
                .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        profileWithCutoff("Asia/Kolkata", java.time.LocalTime.of(4, 0), effectiveFrom);
        Bill bill = billCreatedAt("2026-11-10T18:29:00Z", null); // 2026-11-10 23:59 IST

        service().applyServerBusinessDate(TENANT, bill);

        assertThat(bill.getLastResetDate()).isEqualTo("2026-11-10");
    }

    @Test
    void aCutoffIsIgnoredForDaysBeforeItsEffectiveDate() {
        // Forward-only: the 9th keeps the calendar rule even though 01:30 reads like the 8th's.
        long effectiveFrom = java.time.LocalDateTime.of(2026, 11, 10, 0, 0)
                .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        profileWithCutoff("Asia/Kolkata", java.time.LocalTime.of(4, 0), effectiveFrom);
        Bill bill = billCreatedAt("2026-11-08T20:00:00Z", null); // 2026-11-09 01:30 IST

        service().applyServerBusinessDate(TENANT, bill);

        assertThat(bill.getLastResetDate()).isEqualTo("2026-11-09");
    }
}
