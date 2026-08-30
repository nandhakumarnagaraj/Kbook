package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.ItemVariant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Distributed state problems B1/B2/C3: Server-owned state preservation.
 *
 * B1 – Terminal deactivation: device push cannot reactivate a deactivated user.
 * B2 – Permission revocation: device push cannot restore revoked permissions.
 * C3 – Payment status: device push cannot revert a gateway-confirmed payment.
 */
class ServerOwnedStatePreservationTest {

    // ── B1: User deactivation preservation ────────────────────────────

    @Test
    void user_deactivatedOnServer_cannotBeReactivatedByDevice() {
        User incoming = user(true);   // device thinks user is active
        User existing = user(false);  // server deactivated the user

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsActive()).isFalse();
    }

    @Test
    void user_activeOnServer_notForceActivatedByDevice() {
        User incoming = user(false);
        User existing = user(true);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        // Server says active → device cannot deactivate via sync
        assertThat(incoming.getIsActive()).isTrue();
    }

    @Test
    void user_tokenInvalidation_preservedFromServer() {
        User incoming = user(true);
        incoming.setTokenInvalidatedAt(null); // device doesn't know about revocation
        User existing = user(true);
        existing.setTokenInvalidatedAt(1000L); // server revoked tokens

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getTokenInvalidatedAt()).isEqualTo(1000L);
    }

    @Test
    void user_role_preservedFromServer() {
        User incoming = user(true);
        incoming.setRole(UserRole.CASHIER); // device has stale role
        User existing = user(true);
        existing.setRole(UserRole.MANAGER); // server promoted user

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getRole()).isEqualTo(UserRole.MANAGER);
    }

    // ── B2: Restaurant suspension preservation ────────────────────────

    @Test
    void restaurant_suspendedOnServer_cannotBeUnsuspendedByDevice() {
        RestaurantProfile incoming = restaurantProfile(false); // device thinks not suspended
        RestaurantProfile existing = restaurantProfile(true);  // server suspended it

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsSuspended()).isTrue();
    }

    @Test
    void restaurant_notSuspended_preservedAsIs() {
        RestaurantProfile incoming = restaurantProfile(false);
        RestaurantProfile existing = restaurantProfile(false);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsSuspended()).isFalse();
    }

    // ── C3: Menu item availability (inventory cascade) ────────────────

    @Test
    void menuItem_hiddenByInventoryCascade_cannotBeReactivatedByDevice() {
        MenuItem incoming = menuItem(true);   // device still thinks available
        MenuItem existing = menuItem(false);  // server hid via zero-stock cascade

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isFalse();
    }

    @Test
    void menuItem_ownerCanStillHide() {
        MenuItem incoming = menuItem(false);
        MenuItem existing = menuItem(true);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isFalse();
    }

    @Test
    void itemVariant_hiddenByCascade_cannotBeReactivated() {
        ItemVariant incoming = new ItemVariant();
        incoming.setIsAvailable(true);
        ItemVariant existing = new ItemVariant();
        existing.setIsAvailable(false);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private User user(boolean active) {
        User u = new User();
        u.setIsActive(active);
        u.setRole(UserRole.OWNER);
        u.setRestaurantId(1L);
        return u;
    }

    private RestaurantProfile restaurantProfile(boolean suspended) {
        RestaurantProfile p = new RestaurantProfile();
        p.setIsSuspended(suspended);
        p.setRestaurantId(1L);
        return p;
    }

    private MenuItem menuItem(boolean available) {
        MenuItem m = new MenuItem();
        m.setIsAvailable(available);
        m.setRestaurantId(1L);
        return m;
    }
}
