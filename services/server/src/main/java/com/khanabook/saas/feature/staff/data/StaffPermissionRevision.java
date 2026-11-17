package com.khanabook.saas.feature.staff.data;

import java.io.Serializable;
import java.util.Objects;

/**
 * Historical per-user authorization revision (now constant at 1L).
 */
public class StaffPermissionRevision {
    private Long restaurantId;
    private Long userId;
    private Long revision = 1L;
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
