package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per feature flag (Requirement 30, table created at V48).
 *
 * The dominant kill switch is separate from the rollout default (design D3):
 * enabling a single-restaurant pilot must not switch the feature on for every
 * restaurant that has no override. Both columns default to their safe values in
 * the schema, so any accidentally-inserted row is inert.
 */
@Entity
@Table(name = "feature_flag")
@Getter
@Setter
@NoArgsConstructor
public class FeatureFlag {

    @Id
    @Column(name = "flag_key", nullable = false, length = 64)
    private String flagKey;

    /** Dominant OFF switch (Req 30.8). When true the flag resolves disabled for every restaurant. */
    @Column(name = "kill_switched", nullable = false)
    private boolean killSwitched;

    /** Rollout default for restaurants without an override (Req 30.7). */
    @Column(name = "default_enabled", nullable = false)
    private boolean defaultEnabled;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public FeatureFlag(String flagKey, boolean killSwitched, boolean defaultEnabled, String description,
                       long createdAt, long updatedAt) {
        this.flagKey = flagKey;
        this.killSwitched = killSwitched;
        this.defaultEnabled = defaultEnabled;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
