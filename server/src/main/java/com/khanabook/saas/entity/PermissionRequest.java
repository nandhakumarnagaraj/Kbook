package com.khanabook.saas.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "permission_requests")
public class PermissionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Long requestedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Long resolvedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    public PermissionRequest() {}

    public PermissionRequest(Long restaurantId, Long userId, String permissionKey, String reason) {
        this.restaurantId = restaurantId;
        this.userId = userId;
        this.permissionKey = permissionKey;
        this.reason = reason;
        this.status = "PENDING";
        this.requestedAt = System.currentTimeMillis();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPermissionKey() { return permissionKey; }
    public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Long requestedAt) { this.requestedAt = requestedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public Long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Long resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
