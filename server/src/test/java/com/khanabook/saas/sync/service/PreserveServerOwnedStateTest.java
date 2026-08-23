package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.MenuItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the server-owned availability fix: when the inventory cascade hides
 * a menu item (zero stock), a device menu push must NOT flip it back to
 * available via last-write-wins.
 */
class PreserveServerOwnedStateTest {

    private MenuItem menuItem(boolean available) {
        MenuItem mi = new MenuItem();
        mi.setIsAvailable(available);
        return mi;
    }

    private ItemVariant variant(boolean available) {
        ItemVariant v = new ItemVariant();
        v.setIsAvailable(available);
        return v;
    }

    @Test
    void hiddenItem_staysHidden_whenDevicePushesAvailableTrue() {
        MenuItem incoming = menuItem(true);   // device still thinks it's available
        MenuItem existing = menuItem(false);  // server hid it via zero-stock cascade

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isFalse();
    }

    @Test
    void hiddenVariant_staysHidden_whenDevicePushesAvailableTrue() {
        ItemVariant incoming = variant(true);
        ItemVariant existing = variant(false);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isFalse();
    }

    @Test
    void ownerCanStillHideItem_viaDeviceToggle() {
        // Device marking an item unavailable must keep working.
        MenuItem incoming = menuItem(false);
        MenuItem existing = menuItem(true);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isFalse();
    }

    @Test
    void availableItem_isNotForceChanged() {
        MenuItem incoming = menuItem(true);
        MenuItem existing = menuItem(true);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isTrue();
    }
}
