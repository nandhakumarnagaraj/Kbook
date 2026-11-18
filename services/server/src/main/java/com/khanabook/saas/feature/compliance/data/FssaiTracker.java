package com.khanabook.saas.feature.compliance.data;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * @deprecated Retired in V128. FSSAI details are maintained directly on RestaurantProfile.
 */
@Deprecated
@Getter
@Setter
public class FssaiTracker {

    private Long id;
    private Long restaurantId;
    private String fssaiNumber;
    private String companyName;
    private String address;
    private String status = "UNKNOWN";
    private LocalDate expiryDate;
    private LocalDate applicationSubmissionDate;
    private LocalDate lastUpdatedOn;
    private Long lastCheckedAt;
    private Boolean isAlertActive = true;
    private Long lastAlertSentAt;
}
