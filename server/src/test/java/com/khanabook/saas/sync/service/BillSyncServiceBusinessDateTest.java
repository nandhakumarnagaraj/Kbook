package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
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
}
