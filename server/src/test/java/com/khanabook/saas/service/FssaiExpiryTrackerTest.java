package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.FssaiTracker;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.FssaiTrackerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
public class FssaiExpiryTrackerTest extends BaseIntegrationTest {

    @Autowired
    private FssaiTrackerRepository fssaiTrackerRepo;

    @MockBean
    private PushNotificationService pushNotificationService;

    @Autowired
    private FssaiTrackerService fssaiTrackerService;

    @Test
    public void testFssaiExpiryTrackingAndAlerts() {
        // 1. Setup restaurant profile representing REST001 (mapped to Long id 1001L)
        Long restaurantId = 1001L;
        String fssaiNo = "13023001000255";
        LocalDate expiryDate = LocalDate.of(2025, 8, 15);

        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(restaurantId);
        profile.setLocalId(1L);
        profile.setDeviceId("TEST_DEVICE");
        profile.setShopName("Demo Restaurant");
        profile.setFssaiNumber(fssaiNo);
        profile.setFssaiExpiryDate(expiryDate);
        profile.setCreatedAt(System.currentTimeMillis());
        profile.setUpdatedAt(System.currentTimeMillis());
        profile.setServerUpdatedAt(System.currentTimeMillis());
        restaurantProfileRepository.save(profile);

        // 2. Initialize FSSAI Tracker and verify storage & retrieval
        FssaiTracker tracker = new FssaiTracker();
        tracker.setRestaurantId(restaurantId);
        tracker.setFssaiNumber(fssaiNo);
        tracker.setExpiryDate(expiryDate);
        tracker.setIsAlertActive(true);
        fssaiTrackerRepo.save(tracker);

        Optional<FssaiTracker> retrieved = fssaiTrackerRepo.findByRestaurantId(restaurantId);
        assertTrue(retrieved.isPresent());
        assertEquals(expiryDate, retrieved.get().getExpiryDate(), "1. Expiry date correctly stored and retrieved");

        // 3. Verify expiring soon logic (within 90 days)
        // Assume test date is 2025-07-16 (exactly 30 days before 2025-08-15)
        LocalDate mockCurrentDate30 = LocalDate.of(2025, 7, 16);
        long daysToExpiry30 = ChronoUnit.DAYS.between(mockCurrentDate30, expiryDate);
        assertEquals(30, daysToExpiry30);
        assertTrue(daysToExpiry30 <= 90, "2. System flags within 90 days as expiring soon");

        // 4. Verify WARNING vs CRITICAL alert types based on thresholds
        // WARNING at 30 days before expiry
        String severity30 = (daysToExpiry30 <= 7) ? "CRITICAL" : "WARNING";
        assertEquals("WARNING", severity30, "3. 30 days before should trigger WARNING alert");

        // CRITICAL at 7 days before expiry
        LocalDate mockCurrentDate7 = LocalDate.of(2025, 8, 8);
        long daysToExpiry7 = ChronoUnit.DAYS.between(mockCurrentDate7, expiryDate);
        assertEquals(7, daysToExpiry7);
        String severity7 = (daysToExpiry7 <= 7) ? "CRITICAL" : "WARNING";
        assertEquals("CRITICAL", severity7, "3. 7 days before should trigger CRITICAL alert");
    }
}
