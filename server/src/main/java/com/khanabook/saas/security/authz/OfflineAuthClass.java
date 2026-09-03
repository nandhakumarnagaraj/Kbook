package com.khanabook.saas.security.authz;

import com.khanabook.saas.entity.PermissionKey;

import java.util.Map;

/**
 * Offline authorization classification for each fine-grained permission.
 *
 * <p>Approved product decision (Decision B). The class describes how an operation
 * requiring a given permission is treated when it was created offline and later
 * reaches the server during sync:
 * <ul>
 *   <li>{@link #OFFLINE_ALLOWED} — accept; no permission revalidation (offline-first core).</li>
 *   <li>{@link #REVALIDATED_ON_SYNC} — allowed offline, but the server re-checks the
 *       permission (Decision A strict) at sync.</li>
 *   <li>{@link #ONLINE_ONLY} — requires live authorization; an offline-created op is quarantined.</li>
 *   <li>{@link #NEVER_OFFLINE} — must never be created offline; quarantined for admin review.</li>
 * </ul>
 *
 * <p>Rule kept explicit: classify the OPERATION, not the permission name. Where a
 * permission name implies local action but the code performs a server mutation
 * (e.g. {@code orders.kot_ready} is a REST call today), it is
 * classified by its actual behavior.
 */
public enum OfflineAuthClass {
    OFFLINE_ALLOWED,
    REVALIDATED_ON_SYNC,
    ONLINE_ONLY,
    NEVER_OFFLINE;

    private static final Map<String, OfflineAuthClass> BY_KEY = Map.ofEntries(
        // Billing
        Map.entry("billing.create",   OFFLINE_ALLOWED),
        Map.entry("billing.edit",     OFFLINE_ALLOWED),
        Map.entry("billing.settle",   REVALIDATED_ON_SYNC),
        Map.entry("billing.void",     REVALIDATED_ON_SYNC),
        Map.entry("billing.discount", REVALIDATED_ON_SYNC),
        Map.entry("billing.refund",   ONLINE_ONLY),
        // Menu
        Map.entry("menu.view",                 OFFLINE_ALLOWED),
        Map.entry("menu.toggle_availability",  REVALIDATED_ON_SYNC),
        Map.entry("menu.edit_price",           REVALIDATED_ON_SYNC),
        Map.entry("menu.edit_full",            REVALIDATED_ON_SYNC),
        Map.entry("menu.add_item",             REVALIDATED_ON_SYNC),
        Map.entry("menu.delete_item",          REVALIDATED_ON_SYNC),
        // Orders / KOT
        Map.entry("orders.view",       OFFLINE_ALLOWED),
        Map.entry("orders.kot_view",   OFFLINE_ALLOWED),
        Map.entry("orders.kot_ready",  ONLINE_ONLY),
        Map.entry("orders.kot_void",   REVALIDATED_ON_SYNC),
        // Reports
        Map.entry("reports.day_summary", OFFLINE_ALLOWED),
        Map.entry("reports.full",        ONLINE_ONLY),
        Map.entry("reports.gst",         ONLINE_ONLY),
        Map.entry("reports.export",      ONLINE_ONLY),
        // Staff
        Map.entry("staff.view",        NEVER_OFFLINE),
        Map.entry("staff.add",         NEVER_OFFLINE),
        Map.entry("staff.edit",        NEVER_OFFLINE),
        Map.entry("staff.remove",      NEVER_OFFLINE),
        Map.entry("staff.permissions", NEVER_OFFLINE),
        // Settings
        Map.entry("settings.shop_profile", NEVER_OFFLINE),
        Map.entry("settings.payment",      NEVER_OFFLINE),
        Map.entry("settings.printer",      NEVER_OFFLINE),
        Map.entry("settings.terminal",     NEVER_OFFLINE),
        Map.entry("settings.gst",          NEVER_OFFLINE)
    );

    /**
     * Classification for a permission key. Unknown keys default to the safest
     * posture (NEVER_OFFLINE) so a new/unclassified permission can never be
     * silently accepted from an offline device.
     */
    public static OfflineAuthClass forKey(String permissionKey) {
        if (permissionKey == null) return NEVER_OFFLINE;
        return BY_KEY.getOrDefault(permissionKey, NEVER_OFFLINE);
    }

    /** Guard: every PermissionKey in the enum has an explicit classification. */
    public static boolean allKeysClassified() {
        for (PermissionKey pk : PermissionKey.values()) {
            if (!BY_KEY.containsKey(pk.getKey())) return false;
        }
        return true;
    }
}
