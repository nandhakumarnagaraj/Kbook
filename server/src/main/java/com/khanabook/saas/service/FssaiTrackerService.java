package com.khanabook.saas.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.khanabook.saas.entity.FssaiTracker;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.FssaiTrackerRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class FssaiTrackerService {

    private static final Logger log = LoggerFactory.getLogger(FssaiTrackerService.class);
    private static final String TRACKER_URL = "https://iadv.in/tracker/dist/response.php?application_id=";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestaurantProfileRepository restaurantProfileRepo;
    private final FssaiTrackerRepository fssaiTrackerRepo;
    private final PushNotificationService pushNotificationService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    public FssaiTrackerService(RestaurantProfileRepository restaurantProfileRepo,
                               FssaiTrackerRepository fssaiTrackerRepo,
                               PushNotificationService pushNotificationService) {
        this.restaurantProfileRepo = restaurantProfileRepo;
        this.fssaiTrackerRepo = fssaiTrackerRepo;
        this.pushNotificationService = pushNotificationService;
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

                // 1. Fetch current status from FSSAI Tracker API
                String url = TRACKER_URL + fssaiNo;
                ResponseEntity<FssaiAppResponse[]> responseEntity = restTemplate.getForEntity(url, FssaiAppResponse[].class);
                FssaiAppResponse[] apps = responseEntity.getBody();

                boolean isRenewed = false;
                if (apps != null && apps.length > 0) {
                    FssaiAppResponse app = apps[0];
                    log.info("FSSAI Tracker response for restaurantId={} ({}): statusDesc={}, lastUpdatedOn={}",
                        profile.getRestaurantId(), fssaiNo, app.getStatusDesc(), app.getLastUpdatedOn());

                    tracker.setStatus(app.getStatusDesc() != null ? app.getStatusDesc().toUpperCase() : "UNKNOWN");
                    tracker.setCompanyName(app.getCompanyName());
                    
                    if (app.getAppSubmissionDate() != null) {
                        try {
                            tracker.setApplicationSubmissionDate(LocalDate.parse(app.getAppSubmissionDate(), DATE_FORMATTER));
                        } catch (Exception e) {
                            log.warn("Failed to parse appSubmissionDate: {}", app.getAppSubmissionDate());
                        }
                    }

                    if (app.getLastUpdatedOn() != null) {
                        try {
                            LocalDate lastUpdateDate = LocalDate.parse(app.getLastUpdatedOn(), DATE_FORMATTER);
                            tracker.setLastUpdatedOn(lastUpdateDate);

                            // If renewal has completed and license is issued, calculate new expiry date
                            if ("License Issued".equalsIgnoreCase(app.getStatusDesc())) {
                                LocalDate calculatedExpiry = lastUpdateDate.plusYears(1); // Default FSSAI renewal duration: 1 year
                                tracker.setExpiryDate(calculatedExpiry);

                                // Sync the updated expiry date back to the RestaurantProfile
                                if (profile.getFssaiExpiryDate() == null || calculatedExpiry.isAfter(profile.getFssaiExpiryDate())) {
                                    profile.setFssaiExpiryDate(calculatedExpiry);
                                    profile.setUpdatedAt(System.currentTimeMillis());
                                    profile.setServerUpdatedAt(System.currentTimeMillis());
                                    restaurantProfileRepo.save(profile);
                                    isRenewed = true;
                                    log.info("Auto-updated profile FSSAI expiry date to {} for restaurantId={}", calculatedExpiry, profile.getRestaurantId());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse lastUpdatedOn: {}", app.getLastUpdatedOn());
                        }
                    }
                } else {
                    // Fall back to profile's expiry date if not found in tracker API
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
        String title = "⚠️ FSSAI License Expiring Soon!";
        String message = String.format(
            "Your FSSAI license (%s) for %s will expire in %d days (%s). Please renew it immediately to avoid penalties.",
            profile.getFssaiNumber(),
            shopName,
            daysToExpiry,
            tracker.getExpiryDate().format(DATE_FORMATTER)
        );

        log.info("Sending FSSAI renewal push notification to restaurantId={}", profile.getRestaurantId());

        // Dispatch notification of type "fssai_expiry" to attach actions (Pay Now / Remind Later)
        pushNotificationService.pushToRestaurant(
            profile.getRestaurantId(),
            title,
            message,
            "fssai_expiry",
            profile.getFssaiNumber(), // referenceId is the license number
            "fssai",
            BigDecimal.ZERO
        );
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FssaiAppResponse {
        private String statusDesc;
        private String appSubmissionDate;
        private String lastUpdatedOn;
        private String apptypeDesc;
        private String companyName;
    }
}
