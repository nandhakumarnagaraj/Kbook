package com.khanabook.saas.feature.staff.data;

public class StaffPermission {
    private Long id;
    private Long restaurantId;
    private Long userId;
    private String permissionKey;
    private Boolean granted = true;
    private Long grantedBy;
    private Long grantedAt;
    private Long revokedAt;
    private Long updatedAt;
    private Long lastRevokedRevision;

    public StaffPermission() {}

    public StaffPermission(Long restaurantId, Long userId, String permissionKey, Long grantedBy) {
        this.restaurantId = restaurantId;
        this.userId = userId;
        this.permissionKey = permissionKey;
        this.grantedBy = grantedBy;
        this.granted = true;
        this.grantedAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPermissionKey() { return permissionKey; }
    public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }
    public Boolean getGranted() { return granted; }
    public void setGranted(Boolean granted) { this.granted = granted; }
    public Long getGrantedBy() { return grantedBy; }
    public void setGrantedBy(Long grantedBy) { this.grantedBy = grantedBy; }
    public Long getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Long grantedAt) { this.grantedAt = grantedAt; }
    public Long getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Long revokedAt) { this.revokedAt = revokedAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
    public Long getLastRevokedRevision() { return lastRevokedRevision; }
    public void setLastRevokedRevision(Long lastRevokedRevision) { this.lastRevokedRevision = lastRevokedRevision; }
}
