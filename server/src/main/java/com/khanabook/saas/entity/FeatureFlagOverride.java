package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per (flag, restaurant) pair that has been explicitly overridden
 * (Requirement 30.6, table created at V48).
 *
 * The unique constraint (flag_key, restaurant_id) makes writes idempotent at
 * the database level; the service upserts against it.
 */
@Entity
@Table(name = "feature_flag_override", uniqueConstraints = {
    @UniqueConstraint(name = "uq_feature_flag_override", columnNames = {"flag_key", "restaurant_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class FeatureFlagOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flag_key", nullable = false, length = 64)
    private String flagKey;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public FeatureFlagOverride(String flagKey, Long restaurantId, boolean enabled,
                               long createdAt, long updatedAt) {
        this.flagKey = flagKey;
        this.restaurantId = restaurantId;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
