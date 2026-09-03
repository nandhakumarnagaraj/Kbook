package com.khanabook.saas.security.authz;

import com.khanabook.saas.entity.PermissionKey;

import java.util.List;

/**
 * Classifies what a pushed {@link com.khanabook.saas.entity.MenuItem} actually
 * changes relative to the existing server row, and maps that to the fine-grained
 * permission(s) the acting user must hold.
 *
 * <p>Pure diff semantics — see {@link MenuPushAuthorizer#detect}. The mapping is
 * the single source of truth for "which permission gates which menu mutation":
 * <ul>
 *   <li>{@link #CREATE} → {@code menu.add_item}</li>
 *   <li>{@link #DELETE} → {@code menu.delete_item}</li>
 *   <li>{@link #PRICE} → {@code menu.edit_price}</li>
 *   <li>{@link #AVAILABILITY} → {@code menu.toggle_availability}</li>
 *   <li>{@link #PRICE_AND_AVAILABILITY} → BOTH keys (all required)</li>
 *   <li>{@link #METADATA_ONLY} → {@code menu.edit_price} (name/description/etc. are
 *       edit-grade changes; gated behind the same "edit" authority as price)</li>
 *   <li>{@link #NONE} → no permission required (idempotent/no-op push)</li>
 * </ul>
 *
 * <p>DELETE takes precedence over field diffs: a soft-delete push is authorized
 * as a delete regardless of any incidental field changes in the same payload.
 * CREATE likewise takes precedence — a brand-new row is an add, not an edit.
 */
public enum MenuChangeType {
    NONE(List.of()),
    CREATE(List.of(PermissionKey.MENU_ADD_ITEM.getKey())),
    DELETE(List.of(PermissionKey.MENU_DELETE_ITEM.getKey())),
    PRICE(List.of(PermissionKey.MENU_EDIT_PRICE.getKey())),
    AVAILABILITY(List.of(PermissionKey.MENU_TOGGLE_AVAILABILITY.getKey())),
    PRICE_AND_AVAILABILITY(List.of(
            PermissionKey.MENU_EDIT_PRICE.getKey(),
            PermissionKey.MENU_TOGGLE_AVAILABILITY.getKey())),
    METADATA_ONLY(List.of(PermissionKey.MENU_EDIT_PRICE.getKey()));

    private final List<String> requiredPermissionKeys;

    MenuChangeType(List<String> requiredPermissionKeys) {
        this.requiredPermissionKeys = requiredPermissionKeys;
    }

    /** Permission keys that must ALL be held for this change to be authorized. */
    public List<String> requiredPermissionKeys() {
        return requiredPermissionKeys;
    }

    /**
     * Single source of truth for menu-permission implication: holding
     * {@code menu.edit_full} satisfies both {@code menu.edit_price} and
     * {@code menu.toggle_availability}. Used identically on server and mirrored on
     * Android so the implication is defined in exactly one place per stack (P2).
     *
     * @param requiredKey the permission a change requires
     * @param heldKey     a permission key the user actually holds
     * @return true if holding {@code heldKey} satisfies {@code requiredKey}
     */
    public static boolean satisfies(String requiredKey, String heldKey) {
        if (requiredKey.equals(heldKey)) return true;
        if (!PermissionKey.MENU_EDIT_FULL.getKey().equals(heldKey)) return false;
        return PermissionKey.MENU_EDIT_PRICE.getKey().equals(requiredKey)
                || PermissionKey.MENU_TOGGLE_AVAILABILITY.getKey().equals(requiredKey);
    }
}
