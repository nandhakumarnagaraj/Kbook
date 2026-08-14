package com.khanabook.saas.webadmin.dto;

/**
 * Flag_Admin_Surface row: persisted columns plus the current effective global
 * state (what a restaurant without an override resolves to).
 */
public record FeatureFlagAdminResponse(
        String flagKey,
        boolean killSwitched,
        boolean defaultEnabled,
        String description,
        long updatedAt,
        boolean effectiveState
) {
}