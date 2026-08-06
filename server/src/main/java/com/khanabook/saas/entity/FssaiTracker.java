package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "fssai_tracker")
@Getter
@Setter
public class FssaiTracker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private Long restaurantId;

    @Column(name = "fssai_number")
    private String fssaiNumber;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "status")
    private String status = "UNKNOWN";

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "application_submission_date")
    private LocalDate applicationSubmissionDate;

    @Column(name = "last_updated_on")
    private LocalDate lastUpdatedOn;

    @Column(name = "last_checked_at")
    private Long lastCheckedAt;

    @Column(name = "is_alert_active", nullable = false)
    private Boolean isAlertActive = true;

    @Column(name = "last_alert_sent_at")
    private Long lastAlertSentAt;
}
