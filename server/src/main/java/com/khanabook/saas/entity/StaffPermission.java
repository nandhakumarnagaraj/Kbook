package com.khanabook.saas.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "staff_permissions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "user_id", "permission_key"}))
public class StaffPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;

    @Column(nullable = false)
    private Boolean granted = true;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Long grantedAt;

    @Column(name = "revoked_at")
    private Long revokedAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

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
}
