package com.khanabook.saas.sync.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Distributed state problem D1: Daily order counter merge.
 *
 * When two terminals create bills offline and push, the server merges counters
 * using max(). This can leave gaps in daily order sequences.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CounterMergeTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 9101L;

    @Autowired private GenericSyncService genericSyncService;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;

    @Test
    void mergeCounterState_sameDate_takesMax() {
        RestaurantProfile existing = profile(10L, 5L, "2026-08-30");
        RestaurantProfile incoming = profile(8L, 3L, "2026-08-30");

        mergeCounterState(incoming, existing);

        assertThat(incoming.getLifetimeOrderCounter()).isEqualTo(10L);
        assertThat(incoming.getDailyOrderCounter()).isEqualTo(5L);
    }

    @Test
    void mergeCounterState_existingDateNewer_usesExisting() {
        RestaurantProfile existing = profile(10L, 5L, "2026-08-30");
        RestaurantProfile incoming = profile(8L, 3L, "2026-08-29");

        mergeCounterState(incoming, existing);

        assertThat(incoming.getLifetimeOrderCounter()).isEqualTo(10L);
        assertThat(incoming.getDailyOrderCounter()).isEqualTo(5L);
    }

    @Test
    void mergeCounterState_incomingDateNewer_usesIncoming() {
        RestaurantProfile existing = profile(10L, 5L, "2026-08-29");
        RestaurantProfile incoming = profile(12L, 7L, "2026-08-30");

        mergeCounterState(incoming, existing);

        assertThat(incoming.getLifetimeOrderCounter()).isEqualTo(12L);
        assertThat(incoming.getDailyOrderCounter()).isEqualTo(7L);
    }

    @Test
    void mergeCounterState_equalCounters_noChange() {
        RestaurantProfile existing = profile(10L, 5L, "2026-08-30");
        RestaurantProfile incoming = profile(10L, 5L, "2026-08-30");

        mergeCounterState(incoming, existing);

        assertThat(incoming.getLifetimeOrderCounter()).isEqualTo(10L);
        assertThat(incoming.getDailyOrderCounter()).isEqualTo(5L);
    }

    @Test
    void mergeCounterState_nullExistingDates_usesMax() {
        RestaurantProfile existing = profile(10L, 5L, null);
        RestaurantProfile incoming = profile(8L, 3L, null);

        mergeCounterState(incoming, existing);

        assertThat(incoming.getLifetimeOrderCounter()).isEqualTo(10L);
        assertThat(incoming.getDailyOrderCounter()).isEqualTo(5L);
    }

    @Test
    void mergeCounterState_counterGapScenario() {
        RestaurantProfile existing = profile(3L, 3L, "2026-08-30");
        RestaurantProfile incoming = profile(2L, 2L, "2026-08-30");

        mergeCounterState(incoming, existing);

        assertThat(incoming.getDailyOrderCounter()).isEqualTo(3L);
    }

    @Test
    void mergeCounterState_nullIncomingDate_usesExisting() {
        RestaurantProfile existing = profile(10L, 5L, "2026-08-30");
        RestaurantProfile incoming = profile(8L, 3L, null);

        mergeCounterState(incoming, existing);

        assertThat(incoming.getDailyOrderCounter()).isEqualTo(5L);
        assertThat(incoming.getLastResetDate()).isEqualTo("2026-08-30");
    }

    // ── Helper: reflection to invoke private mergeCounterState ────────

    private void mergeCounterState(RestaurantProfile incoming, RestaurantProfile existing) {
        try {
            Method method = GenericSyncService.class.getDeclaredMethod(
                    "mergeCounterState", RestaurantProfile.class, RestaurantProfile.class);
            method.setAccessible(true);
            method.invoke(genericSyncService, incoming, existing);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke mergeCounterState", e);
        }
    }

    private RestaurantProfile profile(Long lifetime, Long daily, String resetDate) {
        RestaurantProfile p = new RestaurantProfile();
        p.setLifetimeOrderCounter(lifetime);
        p.setDailyOrderCounter(daily);
        p.setLastResetDate(resetDate);
        p.setRestaurantId(RESTAURANT);
        return p;
    }
}
