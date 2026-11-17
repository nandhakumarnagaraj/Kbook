package com.khanabook.saas.feature.staff.data;

public class PermissionRequest {
    private Long id;
    private Long restaurantId;
    private Long userId;
    private String permissionKey;
    private String status = "PENDING";
    private String reason;
    private Long requestedAt;
    private Long resolvedBy;
    private Long resolvedAt;
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
