package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.feature.business.service.BusinessWriteService;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.auth.repository.RefreshTokenRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.service.PasswordResetOtpService;
import com.khanabook.saas.feature.staff.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The web-admin half of favourite dishes: the star is server-authoritative, so this
 * owner-gated service method is its ONLY writer (sync pushes restore the stored value —
 * see {@code MenuItemFavouritePreservationTest}).
 *
 * <p>Beyond "it sets the flag", two behaviours matter: the nudge, because both surfaces
 * render the same synced rows and a pin that never reaches the tills is invisible where it
 * is supposed to help; and idempotence, because every version bump wakes every terminal.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuItemFavouriteTest {

    private static final long RESTAURANT = 7L;
    private static final long ITEM = 501L;

    @Mock private MenuItemRepository menuItemRepository;
    @Mock private PushNotificationService pushNotificationService;

    private BusinessWriteService service;

    @BeforeEach
    void setUp() {
        service = new BusinessWriteService(
                mock(UserRepository.class),
                mock(CategoryRepository.class),
                menuItemRepository,
                mock(ItemVariantRepository.class),
                mock(RestaurantTerminalRepository.class),
                mock(RestaurantProfileRepository.class),
                mock(PermissionService.class),
                mock(PasswordResetOtpService.class),
                mock(RefreshTokenRepository.class),
                pushNotificationService);
    }

    private MenuItem live(boolean favourite, long serverUpdatedAt) {
        MenuItem item = new MenuItem();
        item.setId(ITEM);
        item.setRestaurantId(RESTAURANT);
        item.setCategoryId(42L);
        item.setName("Paneer Tikka");
        item.setIsFavourite(favourite);
        item.setServerUpdatedAt(serverUpdatedAt);
        when(menuItemRepository.findById(ITEM)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(item)).thenReturn(item);
        return item;
    }

    @Test
    @DisplayName("starring a dish sets the flag, bumps the server version, and nudges the tills")
    void starringSetsFlagAndNudges() {
        MenuItem item = live(false, 1_000L);

        MenuItem saved = service.setMenuItemFavourite(RESTAURANT, ITEM, true);

        assertThat(saved.getIsFavourite()).isTrue();
        assertThat(saved.getServerUpdatedAt()).isGreaterThan(1_000L);
        verify(menuItemRepository).save(item);
        verify(pushNotificationService).pushSyncNow(RESTAURANT);
    }

    @Test
    @DisplayName("unstarring clears the flag")
    void unstarringClears() {
        MenuItem item = live(true, 1_000L);

        MenuItem saved = service.setMenuItemFavourite(RESTAURANT, ITEM, false);

        assertThat(saved.getIsFavourite()).isFalse();
        verify(pushNotificationService).pushSyncNow(RESTAURANT);
    }

    @Test
    @DisplayName("the server version stays strictly increasing, so a same-millisecond toggle is not missed")
    void versionStrictlyIncreasing() {
        // A device pulls with `> lastSyncTimestamp`; an equal stamp would be invisible to it.
        long aheadOfWallClock = System.currentTimeMillis() + 60_000L;
        MenuItem item = live(false, aheadOfWallClock);

        MenuItem saved = service.setMenuItemFavourite(RESTAURANT, ITEM, true);

        assertThat(saved.getServerUpdatedAt()).isGreaterThan(aheadOfWallClock);
    }

    @Test
    @DisplayName("re-sending the current value is a no-op: no new version, no wake-up")
    void idempotentNoOp() {
        MenuItem item = live(true, 1_000L);

        MenuItem returned = service.setMenuItemFavourite(RESTAURANT, ITEM, true);

        assertThat(returned.getServerUpdatedAt()).isEqualTo(1_000L);
        verify(menuItemRepository, never()).save(item);
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    @DisplayName("idempotence does not swallow a real change")
    void idempotenceDoesNotSwallowChange() {
        MenuItem item = live(false, 1_000L);

        // Same call as the no-op test, opposite stored value: it must write.
        service.setMenuItemFavourite(RESTAURANT, ITEM, true);

        verify(menuItemRepository).save(item);
        verify(pushNotificationService).pushSyncNow(RESTAURANT);
    }

    @Test
    @DisplayName("an unknown or another tenant's dish is rejected, not silently ignored")
    void otherTenantsDishRejected() {
        MenuItem other = new MenuItem();
        other.setId(ITEM);
        other.setRestaurantId(999L);
        other.setName("Someone else's dish");
        other.setIsFavourite(false);
        when(menuItemRepository.findById(ITEM)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.setMenuItemFavourite(RESTAURANT, ITEM, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Menu item not found");
        verify(menuItemRepository, never()).save(other);
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    @DisplayName("a deleted dish cannot be pinned back into the counter's list")
    void deletedDishRejected() {
        MenuItem deleted = new MenuItem();
        deleted.setId(ITEM);
        deleted.setRestaurantId(RESTAURANT);
        deleted.setName("Retired dish");
        deleted.setIsDeleted(true);
        deleted.setIsFavourite(false);
        when(menuItemRepository.findById(ITEM)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.setMenuItemFavourite(RESTAURANT, ITEM, true))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(pushNotificationService);
    }
}
