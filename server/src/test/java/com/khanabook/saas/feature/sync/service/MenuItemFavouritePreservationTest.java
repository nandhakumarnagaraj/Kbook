package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.MenuItemDTO;
import com.khanabook.saas.feature.sync.data.SyncMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the "the owner's pin must not fight the till" contract for favourite dishes
 * (V109), the menu-item counterpart of {@link CategoryFavouritePreservationTest}.
 *
 * <p>A dish is a worse case than a category was: MenuItem HAS a field mask, so a terminal
 * edits one dish constantly (price, name, photo, availability, category move) and every one
 * of those writes carries the whole row. If the pin were taken from the payload, the first
 * price edit from a till would clear a feature the owner set in web admin — and because both
 * surfaces render the same synced row, it would vanish on the till too and look random.
 *
 * <p>So the pin is server-authoritative and restored on both merge paths: the masked merge
 * (a push that does not name it) and preserveServerOwnedState (a legacy push that sends no
 * mask at all, or one that does name it). A device can neither set nor clear it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuItemFavouritePreservationTest {

    private static final long RESTAURANT = 7L;
    private static final long CATEGORY = 42L;

    private static MenuItem stored(boolean favourite) {
        MenuItem existing = new MenuItem();
        existing.setRestaurantId(RESTAURANT);
        existing.setCategoryId(CATEGORY);
        existing.setName("Paneer Tikka");
        existing.setIsFavourite(favourite);
        return existing;
    }

    private static MenuItem incomingFromTerminal(Boolean favourite) {
        MenuItem incoming = new MenuItem();
        incoming.setRestaurantId(RESTAURANT);
        incoming.setCategoryId(CATEGORY);
        incoming.setName("Paneer Tikka (renamed on till)");
        incoming.setIsFavourite(favourite);
        return incoming;
    }

    // ─── Whole-record pushes (legacy builds, and the pre-lookup normalisation path) ───

    @Test
    @DisplayName("a build that predates the column (field omitted) does not clear the pin")
    void omittedFieldKeepsPin() {
        MenuItem incoming = incomingFromTerminal(null);

        GenericSyncService.preserveServerOwnedState(incoming, stored(true));

        assertThat(incoming.getIsFavourite()).isTrue();
    }

    @Test
    @DisplayName("a terminal sending a stale false still does not clear the pin")
    void staleFalseKeepsPin() {
        MenuItem incoming = incomingFromTerminal(false);

        GenericSyncService.preserveServerOwnedState(incoming, stored(true));

        assertThat(incoming.getIsFavourite()).isTrue();
    }

    @Test
    @DisplayName("a terminal cannot pin a dish either: the stored value wins both ways")
    void terminalCannotPin() {
        MenuItem incoming = incomingFromTerminal(true);

        GenericSyncService.preserveServerOwnedState(incoming, stored(false));

        assertThat(incoming.getIsFavourite()).isFalse();
    }

    @Test
    @DisplayName("a stored null produces false, never null, so the NOT NULL column cannot fail")
    void storedNullBecomesFalse() {
        MenuItem existing = stored(true);
        existing.setIsFavourite(null);
        MenuItem incoming = incomingFromTerminal(null);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsFavourite()).isFalse();
    }

    @Test
    @DisplayName("an unpinned dish stays unpinned whatever a till sends")
    void unpinnedStaysUnpinned() {
        MenuItem incoming = incomingFromTerminal(null);

        GenericSyncService.preserveServerOwnedState(incoming, stored(false));

        assertThat(incoming.getIsFavourite()).isFalse();
    }

    // ─── Masked pushes (the field-mask merge) ───

    @Test
    @DisplayName("a price-only mask keeps the pin the device never mentioned")
    void maskedPushKeepsPin() {
        MenuItem existing = stored(true);
        MenuItem incoming = incomingFromTerminal(false);
        incoming.setChangedFields("basePrice");

        GenericSyncService.applyChangedFieldsMerge(incoming, existing);

        assertThat(incoming.getIsFavourite()).isTrue();
    }

    @Test
    @DisplayName("even a mask that NAMES isFavourite cannot clear the pin")
    void maskNamingFavouriteCannotClear() {
        MenuItem existing = stored(true);
        MenuItem incoming = incomingFromTerminal(false);
        incoming.setChangedFields("isFavourite");

        GenericSyncService.applyChangedFieldsMerge(incoming, existing);

        assertThat(incoming.getIsFavourite())
                .as("the mask protocol covers fields a client owns; this is not one")
                .isTrue();
    }

    // ─── The DTO boundary ───

    @Test
    @DisplayName("the DTO mapper never leaves the NOT NULL column null")
    void nullFromOldClientBecomesFalse() {
        MenuItemDTO dto = new MenuItemDTO();
        dto.setLocalId(1L);
        dto.setRestaurantId(RESTAURANT);
        dto.setCategoryId(CATEGORY);
        dto.setName("Paneer Tikka");
        dto.setIsFavourite(null);

        MenuItem mapped = SyncMapper.mapToEntity(dto, MenuItem.class);

        assertThat(mapped.getIsFavourite()).isFalse();
    }

    @Test
    @DisplayName("the DTO mapper carries a favourite through on create")
    void favouriteCarriedOnCreate() {
        MenuItemDTO dto = new MenuItemDTO();
        dto.setLocalId(1L);
        dto.setRestaurantId(RESTAURANT);
        dto.setCategoryId(CATEGORY);
        dto.setName("Paneer Tikka");
        dto.setIsFavourite(true);

        MenuItem mapped = SyncMapper.mapToEntity(dto, MenuItem.class);

        assertThat(mapped.getIsFavourite()).isTrue();
    }
}
