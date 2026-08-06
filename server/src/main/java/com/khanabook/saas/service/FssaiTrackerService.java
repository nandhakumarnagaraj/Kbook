package com.khanabook.saas.service;

import com.khanabook.saas.entity.FssaiTracker;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.FssaiTrackerRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class FssaiTrackerService {

    private static final Logger log = LoggerFactory.getLogger(FssaiTrackerService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestaurantProfileRepository restaurantProfileRepo;
    private final FssaiTrackerRepository fssaiTrackerRepo;
    private final PushNotificationService pushNotificationService;
    private final GstFssaiLookupService fssaiLookupService;

    @Autowired
    public FssaiTrackerService(RestaurantProfileRepository restaurantProfileRepo,
                               FssaiTrackerRepository fssaiTrackerRepo,
                               PushNotificationService pushNotificationService,
                               GstFssaiLookupService fssaiLookupService) {
        this.restaurantProfileRepo = restaurantProfileRepo;
        this.fssaiTrackerRepo = fssaiTrackerRepo;
        this.pushNotificationService = pushNotificationService;
        this.fssaiLookupService = fssaiLookupService;
    }

    /**
     * Run daily at 10:00 AM to check FSSAI expiry dates and renewal status.
     */
    @Scheduled(cron = "0 0 10 * * ?")
    @Transactional
    public void trackFssaiLicenses() {
        log.info("Starting daily FSSAI license tracking task...");
        List<RestaurantProfile> profiles = restaurantProfileRepo.findAll();

        for (RestaurantProfile profile : profiles) {
            String fssaiNo = profile.getFssaiNumber();
            if (fssaiNo == null || fssaiNo.isBlank()) {
                continue;
            }

            try {
                // Fetch or initialize the FSSAI tracking record for this restaurant
                FssaiTracker tracker = fssaiTrackerRepo.findByRestaurantId(profile.getRestaurantId())
                        .orElseGet(() -> {
                            FssaiTracker t = new FssaiTracker();
                            t.setRestaurantId(profile.getRestaurantId());
                            return t;
                        });

                tracker.setFssaiNumber(fssaiNo);

                // 1. Fetch current license info (authoritative expiry) from pcts.tech
                Map<String, Object> licInfo = fssaiLookupService.lookupFssai(fssaiNo);
                boolean isRenewed = false;
                if (Boolean.TRUE.equals(licInfo.get("valid"))) {
                    log.info("FSSAI license info for restaurantId={} ({}): expiryDate={}, status={}",
                        profile.getRestaurantId(), fssaiNo, licInfo.get("expiryDate"), licInfo.get("fssaiStatus"));

                    tracker.setStatus(String.valueOf(licInfo.getOrDefault("fssaiStatus", "UNKNOWN")).toUpperCase());
                    tracker.setCompanyName(String.valueOf(licInfo.getOrDefault("businessName", "")));
                    tracker.setAddress(String.valueOf(licInfo.getOrDefault("address", "")));

                    String expiryStr = String.valueOf(licInfo.getOrDefault("expiryDate", "")).trim();
                    if (!expiryStr.isEmpty()) {
                        try {
                            LocalDate expiryDate = LocalDate.parse(expiryStr, DATE_FORMATTER);
                            tracker.setExpiryDate(expiryDate);

                            // Sync the authoritative expiry date back to the RestaurantProfile
                            if (profile.getFssaiExpiryDate() == null || expiryDate.isAfter(profile.getFssaiExpiryDate())) {
                                profile.setFssaiExpiryDate(expiryDate);
                                profile.setUpdatedAt(System.currentTimeMillis());
                                profile.setServerUpdatedAt(System.currentTimeMillis());
                                restaurantProfileRepo.save(profile);
                                isRenewed = true;
                                log.info("Auto-updated profile FSSAI expiry date to {} for restaurantId={}", expiryDate, profile.getRestaurantId());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse expiryDate {} for restaurantId={}: {}", expiryStr, profile.getRestaurantId(), e.getMessage());
                        }
                    }
                } else {
                    // Fall back to profile's expiry date if license lookup failed
                    log.warn("FSSAI license lookup failed for restaurantId={} ({}): {}",
                        profile.getRestaurantId(), fssaiNo, licInfo.get("error"));
                    if (profile.getFssaiExpiryDate() != null && tracker.getExpiryDate() == null) {
                        tracker.setExpiryDate(profile.getFssaiExpiryDate());
                    }
                }

                tracker.setLastCheckedAt(System.currentTimeMillis());
                fssaiTrackerRepo.save(tracker);

                // 2. Check if the FSSAI license is expiring and needs warning alerts
                if (tracker.getExpiryDate() != null && Boolean.TRUE.equals(tracker.getIsAlertActive())) {
                    long daysToExpiry = ChronoUnit.DAYS.between(LocalDate.now(), tracker.getExpiryDate());
                    log.info("RestaurantId={} FSSAI Tracker Expiry ({}) is in {} days (date: {})",
                        profile.getRestaurantId(), fssaiNo, daysToExpiry, tracker.getExpiryDate());

                    // Alert on specific milestone days (e.g. 30, 15, 7, 3, 1 day before expiry)
                    if (daysToExpiry == 30 || daysToExpiry == 15 || daysToExpiry == 7 || daysToExpiry == 3 || daysToExpiry == 1) {
                        sendRenewalNotification(profile, tracker, daysToExpiry);
                        tracker.setLastAlertSentAt(System.currentTimeMillis());
                        fssaiTrackerRepo.save(tracker);
                    }
                }

            } catch (Exception e) {
                log.error("Failed to track FSSAI license for restaurantId={} ({}): {}",
                    profile.getRestaurantId(), fssaiNo, e.getMessage(), e);
            }
        }
    }

    private void sendRenewalNotification(RestaurantProfile profile, FssaiTracker tracker, long daysToExpiry) {
        String shopName = profile.getShopName() != null ? profile.getShopName() : "Your restaurant";
        String severity = (daysToExpiry <= 7) ? "CRITICAL" : "WARNING";
        String title = (daysToExpiry <= 7) ? "🚨 FSSAI License Expiring Critical!" : "⚠️ FSSAI License Expiring Soon!";
        String customFssai = profile.getCustomFssaiMessage();
        String message;
        if (customFssai != null && !customFssai.isBlank()) {
            message = customFssai
                .replace("{fssaiNo}", profile.getFssaiNumber() != null ? profile.getFssaiNumber() : "")
                .replace("{shopName}", shopName)
                .replace("{days}", String.valueOf(daysToExpiry))
                .replace("{expiryDate}", tracker.getExpiryDate().format(DATE_FORMATTER));
        } else {
            message = String.format(
                "Your FSSAI license (%s) for %s will expire in %d days (%s). Please renew it immediately to avoid penalties.",
                profile.getFssaiNumber(),
                shopName,
                daysToExpiry,
                tracker.getExpiryDate().format(DATE_FORMATTER)
            );
        }

        log.info("Sending FSSAI renewal push notification to restaurantId={} severity={}", profile.getRestaurantId(), severity);

        // Dispatch notification of type "fssai_expiry" to attach actions (Pay Now / Remind Later)
        pushNotificationService.pushToRestaurant(
            profile.getRestaurantId(),
            title,
            message,
            "fssai_expiry",
            profile.getFssaiNumber(), // referenceId is the license number
            severity,                 // referenceType acts as severity payload
            BigDecimal.ZERO
        );
    }
}
