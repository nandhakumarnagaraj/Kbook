package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/**
 * Monotonic per-user authorization revision. Bumped on any staff permission
 * grant/revoke/template/role change. Separate from terminal credential_version.
 * Synced to Android; the sync path uses it (with StaffPermission.lastRevokedRevision)
 * to decide whether an offline-created operation was authorized.
 */
@Entity
@Table(name = "staff_permission_revision")
@IdClass(StaffPermissionRevision.PK.class)
public class StaffPermissionRevision {

    @Id
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "revision", nullable = false)
    private Long revision = 1L;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    public StaffPermissionRevision() {}

    public StaffPermissionRevision(Long restaurantId, Long userId, Long revision) {
        this.restaurantId = restaurantId;
        this.userId = userId;
        this.revision = revision;
        this.updatedAt = System.currentTimeMillis();
    }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }

    public static class PK implements Serializable {
        private Long restaurantId;
        private Long userId;
        public PK() {}
        public PK(Long restaurantId, Long userId) { this.restaurantId = restaurantId; this.userId = userId; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(restaurantId, pk.restaurantId) && Objects.equals(userId, pk.userId);
        }
        @Override public int hashCode() { return Objects.hash(restaurantId, userId); }
    }
}
