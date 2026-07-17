package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a pending device-registration request. A request is NOT a terminal —
 * it does not receive a terminal series, token, or invoice sequence until approved.
 */
@Entity
@Table(name = "device_registration_request", indexes = {
        @Index(name = "idx_device_request_restaurant_status", columnList = "restaurant_id, status"),
        @Index(name = "idx_device_request_restaurant_requested", columnList = "restaurant_id, requested_at")
})
@Getter
@Setter
public class DeviceRegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "device_name")
    private String deviceName;

    /** NEW_DEVICE, RECOVERY, REPLACEMENT */
    @Column(name = "request_type", nullable = false)
    private String requestType = "NEW_DEVICE";

    /** PENDING, APPROVED, REJECTED, EXPIRED */
    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    /** For RECOVERY/REPLACEMENT: which existing terminal to recover */
    @Column(name = "matched_terminal_id")
    private Long matchedTerminalId;

    @Column(name = "requested_at", nullable = false)
    private Long requestedAt;

    @Column(name = "processed_at")
    private Long processedAt;

    @Column(name = "processed_by_user_id")
    private Long processedByUserId;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** Set after approval — the terminal that was created or recovered */
    @Column(name = "assigned_terminal_id")
    private Long assignedTerminalId;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}
