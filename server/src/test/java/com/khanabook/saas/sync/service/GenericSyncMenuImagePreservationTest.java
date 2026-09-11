package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.MenuItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug fix: a menu item's photo (imageUrl / imageVersion) is server-owned — it is set only
 * via POST /menus/items/{id}/image, never by a device push. A device menu push omits the
 * image fields (incoming null), so the merge must restore the existing server image instead
 * of nulling it and re-propagating the loss.
 *
 * Production chains applyChangedFieldsMerge → preserveServerOwnedState on every push, so the
 * tests exercise the same chain for both a field-masked edit ("basePrice") and a full
 * overwrite ("all", where the field-mask merge is a no-op and preserveServerOwnedState is
 * the sole guard).
 */
class GenericSyncMenuImagePreservationTest {

    private static MenuItem existingWithImage() {
        MenuItem existing = new MenuItem();
        existing.setImageUrl("https://cdn/dish-42.jpg");
        existing.setImageVersion(3);
        existing.setBasePrice(new BigDecimal("250"));
        existing.setName("Paneer Tikka");
        return existing;
    }

    private static MenuItem incomingPriceEditWithoutImage(String mask) {
        MenuItem incoming = new MenuItem();
        // Device edited only the price; image fields are absent from the push payload.
        incoming.setImageUrl(null);
        incoming.setImageVersion(null);
        incoming.setBasePrice(new BigDecimal("300"));
        incoming.setName("Paneer Tikka");
        incoming.setChangedFields(mask);
        return incoming;
    }

    @Test
    void basePriceEditWithNullImageKeepsExistingServerImage() {
        MenuItem existing = existingWithImage();
        MenuItem incoming = incomingPriceEditWithoutImage("basePrice");

        GenericSyncService.applyChangedFieldsMerge(incoming, existing);
        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getImageUrl()).isEqualTo("https://cdn/dish-42.jpg");
        assertThat(incoming.getImageVersion()).isEqualTo(3);
        // The field the device actually changed still wins.
        assertThat(incoming.getBasePrice()).isEqualByComparingTo("300");
    }

    @Test
    void fullOverwriteWithNullImageKeepsExistingServerImage() {
        MenuItem existing = existingWithImage();
        // "all" => field-mask merge is a no-op; preserveServerOwnedState must still protect
        // the server-owned image.
        MenuItem incoming = incomingPriceEditWithoutImage("all");

        GenericSyncService.applyChangedFieldsMerge(incoming, existing);
        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getImageUrl()).isEqualTo("https://cdn/dish-42.jpg");
        assertThat(incoming.getImageVersion()).isEqualTo(3);
    }
}
