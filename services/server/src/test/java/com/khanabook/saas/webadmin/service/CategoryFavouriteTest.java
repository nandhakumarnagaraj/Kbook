package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.feature.business.service.BusinessWriteService;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the web-admin half of the favourite-categories feature: the star is a
 * server-authoritative field, so its ONLY writer is this owner-gated service method.
 *
 * Two behaviours matter beyond "it sets the flag":
 *   - the nudge: both surfaces render the same synced rows, so a star set here has to
 *     reach the tills. Without pushSyncNow it waits for the periodic sync window.
 *   - idempotence: re-sending the same value must not publish a new server version,
 *     because every version bump wakes every terminal for nothing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryFavouriteTest {

    private static final long RESTAURANT = 7L;
    private static final long CATEGORY = 42L;

    @Mock private CategoryRepository categoryRepository;
    @Mock private PushNotificationService pushNotificationService;

    private BusinessWriteService service;

    @BeforeEach
    void setUp() {
        service = new BusinessWriteService(
                mock(UserRepository.class),
                categoryRepository,
                mock(MenuItemRepository.class),
                mock(ItemVariantRepository.class),
                mock(RestaurantTerminalRepository.class),
                mock(RestaurantProfileRepository.class),
                mock(PermissionService.class),
                mock(PasswordResetOtpService.class),
                mock(RefreshTokenRepository.class),
                pushNotificationService);
    }

    private Category live(boolean favourite, long serverUpdatedAt) {
        Category category = new Category();
        category.setId(CATEGORY);
        category.setRestaurantId(RESTAURANT);
        category.setName("Starters");
        category.setIsFavourite(favourite);
        category.setServerUpdatedAt(serverUpdatedAt);
        when(categoryRepository.findByIdAndRestaurantIdAndIsDeletedFalse(CATEGORY, RESTAURANT))
                .thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);
        return category;
    }

    @Test
    @DisplayName("starring a category sets the flag, bumps server version, and nudges the tills")
    void starringSetsFlagAndNudges() {
        Category category = live(false, 1_000L);

        Category saved = service.setCategoryFavourite(RESTAURANT, CATEGORY, true);

        assertThat(saved.getIsFavourite()).isTrue();
        assertThat(saved.getServerUpdatedAt()).isGreaterThan(1_000L);
        verify(categoryRepository).save(category);
        verify(pushNotificationService).pushSyncNow(RESTAURANT);
    }

    @Test
    @DisplayName("unstarring clears the flag")
    void unstarringClearsFlag() {
        Category category = live(true, 1_000L);

        Category saved = service.setCategoryFavourite(RESTAURANT, CATEGORY, false);

        assertThat(saved.getIsFavourite()).isFalse();
        assertThat(saved.getServerUpdatedAt()).isGreaterThan(1_000L);
        verify(categoryRepository).save(category);
        verify(pushNotificationService).pushSyncNow(RESTAURANT);
    }

    @Test
    @DisplayName("server version stays strictly increasing, so a same-millisecond toggle is not missed")
    void versionIsStrictlyIncreasing() {
        // A stored version far in the future forces touch() to take previous + 1 rather than
        // the clock, proving the monotonic guarantee does not depend on time passing between
        // two toggles (a device pull uses `serverUpdatedAt > lastSyncTimestamp`).
        long future = 9_999_999_999_999L;
        Category category = live(false, future);

        Category saved = service.setCategoryFavourite(RESTAURANT, CATEGORY, true);

        assertThat(saved.getServerUpdatedAt()).isEqualTo(future + 1);
    }

    @Test
    @DisplayName("re-sending the current value is a no-op: no new version, no wake-up")
    void idempotentWhenUnchanged() {
        Category category = live(true, 1_000L);

        Category saved = service.setCategoryFavourite(RESTAURANT, CATEGORY, true);

        assertThat(saved.getServerUpdatedAt()).isEqualTo(1_000L);
        verify(categoryRepository, never()).save(category);
        verify(pushNotificationService, never()).pushSyncNow(RESTAURANT);
    }

    @Test
    @DisplayName("idempotence does not swallow a real change")
    void changedValueStillPersists() {
        live(false, 1_000L);

        service.setCategoryFavourite(RESTAURANT, CATEGORY, true);

        verify(categoryRepository, times(1)).save(org.mockito.ArgumentMatchers.any(Category.class));
    }

    @Test
    @DisplayName("an unknown or another tenant's category is rejected, not silently ignored")
    void unknownCategoryRejected() {
        when(categoryRepository.findByIdAndRestaurantIdAndIsDeletedFalse(CATEGORY, RESTAURANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCategoryFavourite(RESTAURANT, CATEGORY, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Category not found");
        verifyNoInteractions(pushNotificationService);
    }
}
