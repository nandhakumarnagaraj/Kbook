package com.khanabook.saas.service;

import java.util.Map;

/**
 * Feature flag resolution and mutation (Requirement 30, tables created at V48).
 *
 * Resolution is a five-step chain (design section 1):
 * <ol>
 *   <li>flag row absent for key        → DISABLED  (Req 30.11)</li>
 *   <li>FeatureConfigGuard fails        → DISABLED  (Req 30.10) — dominates persisted state</li>
 *   <li>kill_switched = true            → DISABLED  (Req 30.8)  — dominates override</li>
 *   <li>override row exists for restaurant → override.enabled (Req 30.6)</li>
 *   <li>otherwise                       → default_enabled      (Req 30.7)</li>
 * </ol>
 *
 * Mutations are restricted by the caller to {@code KBOOK_ADMIN} (see
 * Flag_Admin_Surface); every mutation writes a feature_flag_audit row and
 * invalidates the cached value eagerly on this instance.
 */
public interface FeatureFlagService {

    /** ENABLED | DISABLED, as stored in feature_flag_audit. */
    enum FlagState {
        ENABLED, DISABLED
    }

    FlagState resolve(String flagKey, Long restaurantId);

    boolean isEnabled(String flagKey, Long restaurantId);

    /** Effective state of every known flag for one restaurant. */
    Map<String, Boolean> resolveAllForRestaurant(Long restaurantId);

    /**
     * Global tier used by the webhook inbox before any restaurant resolution:
     * row present, not kill-switched, config guard satisfied. Independent of
     * restaurant (Requirement 33.35).
     */
    boolean isProviderProcessable(String flagKey);

    void setKillSwitch(String flagKey, boolean killSwitched);

    void setDefault(String flagKey, boolean defaultEnabled);

    void setOverride(String flagKey, Long restaurantId, boolean enabled);

    void clearOverride(String flagKey, Long restaurantId);
}
