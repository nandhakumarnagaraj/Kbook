package com.khanabook.saas.security.authz;

import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.PermissionKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MenuPushAuthorizerTest {

    private static final String PRICE = PermissionKey.MENU_EDIT_PRICE.getKey();
    private static final String AVAIL = PermissionKey.MENU_TOGGLE_AVAILABILITY.getKey();
    private static final String ADD = PermissionKey.MENU_ADD_ITEM.getKey();
    private static final String DELETE = PermissionKey.MENU_DELETE_ITEM.getKey();

    private MenuItem item(BigDecimal price, Boolean available, Boolean deleted) {
        MenuItem m = new MenuItem();
        m.setName("Biryani");
        m.setBasePrice(price);
        m.setIsAvailable(available);
        m.setIsDeleted(deleted);
        return m;
    }

    /** Resolver granting a fixed set of keys, never revoked. */
    private MenuPushAuthorizer.FactsResolver granting(String... keys) {
        var set = new java.util.HashSet<>(java.util.List.of(keys));
        return MenuPushAuthorizer.factsResolver(set::contains, k -> null);
    }

    // ── detect() ─────────────────────────────────────────────────────────────

    @Test
    void detect_create_whenNoExistingRow() {
        assertEquals(MenuChangeType.CREATE,
                MenuPushAuthorizer.detect(item(new BigDecimal("250"), true, false), null));
    }

    @Test
    void detect_priceOnly() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), true, false);
        assertEquals(MenuChangeType.PRICE, MenuPushAuthorizer.detect(incoming, existing));
    }

    @Test
    void detect_availabilityOnly() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("250"), false, false);
        assertEquals(MenuChangeType.AVAILABILITY, MenuPushAuthorizer.detect(incoming, existing));
    }

    @Test
    void detect_priceAndAvailability() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), false, false);
        assertEquals(MenuChangeType.PRICE_AND_AVAILABILITY, MenuPushAuthorizer.detect(incoming, existing));
    }

    @Test
    void detect_deleteTakesPrecedenceOverFieldChanges() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), false, true);
        assertEquals(MenuChangeType.DELETE, MenuPushAuthorizer.detect(incoming, existing));
    }

    @Test
    void detect_priceScaleDifferenceIsNotAChange() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("250.00"), true, false);
        assertEquals(MenuChangeType.NONE, MenuPushAuthorizer.detect(incoming, existing));
    }

    @Test
    void detect_nullIncomingFieldsAreNotChanges() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(null, null, false);
        assertEquals(MenuChangeType.NONE, MenuPushAuthorizer.detect(incoming, existing));
    }

    // ── authorize() ───────────────────────────────────────────────────────────

    @Test
    void authorize_priceChange_withoutEditPrice_rejected() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), true, false);
        var r = MenuPushAuthorizer.authorize(incoming, existing, 5L, true, granting(AVAIL));
        assertEquals(OfflineAuthDecider.Decision.REJECT, r.decision());
        assertEquals(MenuChangeType.PRICE, r.changeType());
    }

    @Test
    void authorize_priceChange_withEditPrice_accepted() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), true, false);
        var r = MenuPushAuthorizer.authorize(incoming, existing, 5L, true, granting(PRICE));
        assertEquals(OfflineAuthDecider.Decision.ACCEPT, r.decision());
    }

    @Test
    void authorize_availabilityChange_withoutToggle_rejected() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("250"), false, false);
        var r = MenuPushAuthorizer.authorize(incoming, existing, 5L, true, granting(PRICE));
        assertEquals(OfflineAuthDecider.Decision.REJECT, r.decision());
        assertEquals(MenuChangeType.AVAILABILITY, r.changeType());
    }

    @Test
    void authorize_priceAndAvailability_needsBoth_rejectedWithOnlyOne() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), false, false);
        var r = MenuPushAuthorizer.authorize(incoming, existing, 5L, true, granting(PRICE));
        assertEquals(OfflineAuthDecider.Decision.REJECT, r.decision());
    }

    @Test
    void authorize_priceAndAvailability_acceptedWithBoth() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), false, false);
        var r = MenuPushAuthorizer.authorize(incoming, existing, 5L, true, granting(PRICE, AVAIL));
        assertEquals(OfflineAuthDecider.Decision.ACCEPT, r.decision());
    }

    @Test
    void authorize_create_requiresAddItem() {
        MenuItem incoming = item(new BigDecimal("250"), true, false);
        assertEquals(OfflineAuthDecider.Decision.REJECT,
                MenuPushAuthorizer.authorize(incoming, null, 5L, true, granting(PRICE)).decision());
        assertEquals(OfflineAuthDecider.Decision.ACCEPT,
                MenuPushAuthorizer.authorize(incoming, null, 5L, true, granting(ADD)).decision());
    }

    @Test
    void authorize_delete_requiresDeleteItem() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("250"), true, true);
        assertEquals(OfflineAuthDecider.Decision.REJECT,
                MenuPushAuthorizer.authorize(incoming, existing, 5L, true, granting(PRICE)).decision());
        assertEquals(OfflineAuthDecider.Decision.ACCEPT,
                MenuPushAuthorizer.authorize(incoming, existing, 5L, true, granting(DELETE)).decision());
    }

    @Test
    void authorize_revokedAfterCreation_rejected() {
        // Decision A strict: price edit created at revision 5, key revoked at revision 6.
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), true, false);
        Map<String, Long> revoked = new HashMap<>();
        revoked.put(PRICE, 6L);
        var resolver = MenuPushAuthorizer.factsResolver(
                k -> k.equals(PRICE),      // still granted now (re-granted)
                revoked::get);             // but revoked at revision 6
        var r = MenuPushAuthorizer.authorize(incoming, existing, 5L, true, resolver);
        assertEquals(OfflineAuthDecider.Decision.REJECT, r.decision());
        assertEquals("REVOKED_AFTER_CREATION", r.reason());
    }

    @Test
    void authorize_revokedBeforeCreation_accepted() {
        // Revoked at revision 3, op created later at revision 5 while re-granted.
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), true, false);
        Map<String, Long> revoked = new HashMap<>();
        revoked.put(PRICE, 3L);
        var resolver = MenuPushAuthorizer.factsResolver(k -> k.equals(PRICE), revoked::get);
        var r = MenuPushAuthorizer.authorize(incoming, existing, 5L, true, resolver);
        assertEquals(OfflineAuthDecider.Decision.ACCEPT, r.decision());
    }

    @Test
    void authorize_missingCreationRevision_granted_acceptedUnderP0Fallback() {
        // P0-without-P1: no creation revision yet. A currently-granted key is accepted
        // (grant gate still enforced), rather than quarantining every legitimate edit.
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), true, false);
        var r = MenuPushAuthorizer.authorize(incoming, existing, null, true, granting(PRICE));
        assertEquals(OfflineAuthDecider.Decision.ACCEPT, r.decision());
        assertEquals("GRANTED_NO_REVISION_YET", r.reason());
    }

    @Test
    void authorize_missingCreationRevision_notGranted_rejected() {
        // The grant gate is never bypassed by the fallback.
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), true, false);
        var r = MenuPushAuthorizer.authorize(incoming, existing, null, true, granting(AVAIL));
        assertEquals(OfflineAuthDecider.Decision.REJECT, r.decision());
    }

    @Test
    void authorize_terminalRevoked_rejectedEvenForNoOp() {
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("250"), true, false); // NONE
        var r = MenuPushAuthorizer.authorize(incoming, existing, 5L, false, granting(PRICE));
        assertEquals(OfflineAuthDecider.Decision.REJECT, r.decision());
        assertEquals("TERMINAL_REVOKED", r.reason());
    }

    @Test
    void editFull_satisfies_priceAndAvailability() {
        String editFull = PermissionKey.MENU_EDIT_FULL.getKey();
        MenuItem existing = item(new BigDecimal("250"), true, false);
        MenuItem incoming = item(new BigDecimal("300"), false, false); // price + availability
        // Holds only menu.edit_full — must satisfy both required keys via the implication.
        var resolver = MenuPushAuthorizer.factsResolver(
                key -> MenuChangeType.satisfies(key, editFull), k -> null);
        var r = MenuPushAuthorizer.authorize(incoming, existing, 5L, true, resolver);
        assertEquals(OfflineAuthDecider.Decision.ACCEPT, r.decision());
    }

    @Test
    void satisfies_implicationRules() {
        String editFull = PermissionKey.MENU_EDIT_FULL.getKey();
        assertTrue(MenuChangeType.satisfies(PRICE, editFull));
        assertTrue(MenuChangeType.satisfies(AVAIL, editFull));
        assertTrue(MenuChangeType.satisfies(PRICE, PRICE));
        // edit_full does NOT imply add/delete
        assertFalse(MenuChangeType.satisfies(ADD, editFull));
        assertFalse(MenuChangeType.satisfies(DELETE, editFull));
    }
}
