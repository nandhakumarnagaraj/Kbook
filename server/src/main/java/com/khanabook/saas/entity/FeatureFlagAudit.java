package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Change history for a feature flag (Requirement 30.20, table created at V48).
 *
 * Every mutation writes one row capturing the flag, scope (KILL_SWITCH,
 * DEFAULT or OVERRIDE), the states before and after, and the acting user.
 * {@code previous_state} is ABSENT when the mutation created the subject row.
 */
@Entity
@Table(name = "feature_flag_audit", indexes = {
    @Index(name = "idx_feature_flag_audit_flag", columnList = "flag_key, changed_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class FeatureFlagAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flag_key", nullable = false, length = 64)
    private String flagKey;

    /** KILL_SWITCH | DEFAULT | OVERRIDE */
    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    /** NULL for KILL_SWITCH and DEFAULT scopes. */
    @Column(name = "restaurant_id")
    private Long restaurantId;

    /** ENABLED | DISABLED | ABSENT */
    @Column(name = "previous_state", length = 16)
    private String previousState;

    /** ENABLED | DISABLED */
    @Column(name = "new_state", nullable = false, length = 16)
    private String newState;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 255)
    private String actorUsername;

    @Column(name = "changed_at", nullable = false)
    private long changedAt;

    public FeatureFlagAudit(String flagKey, String scope, Long restaurantId,
                            String previousState, String newState,
                            Long actorUserId, String actorUsername, long changedAt) {
        this.flagKey = flagKey;
        this.scope = scope;
        this.restaurantId = restaurantId;
        this.previousState = previousState;
        this.newState = newState;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.changedAt = changedAt;
    }
}
