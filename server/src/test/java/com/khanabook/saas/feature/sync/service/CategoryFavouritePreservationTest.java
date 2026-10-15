package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.CategoryDTO;
import com.khanabook.saas.feature.sync.data.SyncMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the "the star must not fight itself" contract for favourite categories.
 *
 * Categories carry NO field mask, so a terminal pushes the whole row (rename, reorder,
 * active toggle). is_favourite is therefore server-authoritative rather than client-owned:
 * the owner-gated web-admin endpoint is its only writer, and every category update restores
 * the stored value. Without that, starring a category in web admin and then renaming it from
 * a till would silently clear the pin — on both surfaces, because they render the same
 * synced row.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryFavouritePreservationTest {

    private static Category stored(boolean favourite) {
        Category existing = new Category();
        existing.setRestaurantId(7L);
        existing.setName("Starters");
        existing.setIsFavourite(favourite);
        return existing;
    }

    private static Category incomingFromTerminal(Boolean favourite) {
        Category incoming = new Category();
        incoming.setRestaurantId(7L);
        incoming.setName("Starters (renamed on till)");
        incoming.setIsFavourite(favourite);
        return incoming;
    }

    @Test
    @DisplayName("a build that predates the column (field omitted) does not clear the pin")
    void omittedFieldKeepsPin() {
        Category existing = stored(true);
        Category incoming = incomingFromTerminal(null);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsFavourite()).isTrue();
    }

    @Test
    @DisplayName("a terminal sending a stale false still does not clear the pin")
    void staleFalseKeepsPin() {
        Category incoming = incomingFromTerminal(false);

        GenericSyncService.preserveServerOwnedState(incoming, stored(true));

        // The name change from the till still lands; only the server-owned star survives.
        assertThat(incoming.getName()).isEqualTo("Starters (renamed on till)");
        assertThat(incoming.getIsFavourite()).isTrue();
    }

    @Test
    @DisplayName("a terminal cannot pin a category either — the value is restored, not merged")
    void clientCannotPin() {
        Category incoming = incomingFromTerminal(true);

        GenericSyncService.preserveServerOwnedState(incoming, stored(false));

        assertThat(incoming.getIsFavourite()).isFalse();
    }

    @Test
    @DisplayName("a stored null produces false, never null, so the NOT NULL column cannot fail")
    void storedNullBecomesFalse() {
        Category existing = stored(true);
        existing.setIsFavourite(null);
        Category incoming = incomingFromTerminal(null);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsFavourite()).isFalse();
    }

    @Test
    @DisplayName("the DTO mapper defaults a missing flag to false on create")
    void mapperDefaultsMissingFlag() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Beverages");
        dto.setRestaurantId(7L);

        Category entity = SyncMapper.mapToEntity(dto, Category.class);

        assertThat(entity.getIsFavourite()).isFalse();
    }

    @Test
    @DisplayName("the DTO mapper carries a favourite through on create")
    void mapperCarriesFavourite() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Beverages");
        dto.setRestaurantId(7L);
        dto.setIsFavourite(true);

        Category entity = SyncMapper.mapToEntity(dto, Category.class);

        assertThat(entity.getIsFavourite()).isTrue();
    }
}
