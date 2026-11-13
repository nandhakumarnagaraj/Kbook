package com.khanabook.saas.feature.business.service;

import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileDTO;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;

/**
 * The settings form may change contact details but must not move the owner's
 * login identifier: that only happens after the OTP sent to the new value is
 * verified. These tests pin the guard in {@code updateProfile}.
 */
class BusinessWriteServiceProfileIdentityTest {

    private static final Long TENANT_ID = 7L;
    private static final String MOBILE = "9876543210";
    private static final String EMAIL = "owner@example.com";

    private UserRepository userRepository;
    private RestaurantProfileRepository profileRepository;
    private BusinessWriteService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileRepository = mock(RestaurantProfileRepository.class);
        service = new BusinessWriteService(userRepository, mock(CategoryRepository.class),
                mock(MenuItemRepository.class), mock(ItemVariantRepository.class),
                mock(RestaurantTerminalRepository.class), profileRepository,
                mock(com.khanabook.saas.feature.staff.service.PermissionService.class),
                mock(com.khanabook.saas.feature.auth.service.PasswordResetOtpService.class),
                mock(com.khanabook.saas.feature.auth.repository.RefreshTokenRepository.class),
                mock(com.khanabook.saas.feature.notifications.service.PushNotificationService.class));

        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(TENANT_ID);
        profile.setWhatsappNumber(MOBILE);
        profile.setEmail(EMAIL);
        when(profileRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(RestaurantProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByRestaurantIdAndRoleAndIsDeletedFalse(TENANT_ID, UserRole.OWNER))
                .thenReturn(List.of(owner()));
    }

    private User owner() {
        User owner = new User();
        owner.setId(1L);
        owner.setRestaurantId(TENANT_ID);
        owner.setRole(UserRole.OWNER);
        owner.setWhatsappNumber(MOBILE);
        owner.setEmail(EMAIL);
        return owner;
    }

    @Test
    void rejectsWhatsappNumberThatWasNotOtpVerified() {
        RestaurantProfileDTO dto = new RestaurantProfileDTO();
        dto.setWhatsappNumber("9123456780");

        assertThatThrownBy(() -> service.updateProfile(TENANT_ID, dto))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);
    }

    @Test
    void rejectsEmailThatWasNotOtpVerified() {
        RestaurantProfileDTO dto = new RestaurantProfileDTO();
        dto.setEmail("attacker@example.com");

        assertThatThrownBy(() -> service.updateProfile(TENANT_ID, dto))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptsIdentityEchoedBackInAnotherFormat() {
        RestaurantProfileDTO dto = new RestaurantProfileDTO();
        dto.setWhatsappNumber("+91 98765 43210");
        dto.setEmail("  OWNER@Example.com ");
        dto.setShopName("New Name");

        assertThatCode(() -> service.updateProfile(TENANT_ID, dto)).doesNotThrowAnyException();
    }

    @Test
    void profileUpdateWithoutIdentityFieldsIsUntouched() {
        RestaurantProfileDTO dto = new RestaurantProfileDTO();
        dto.setShopName("New Name");

        assertThat(service.updateProfile(TENANT_ID, dto).getShopName()).isEqualTo("New Name");
    }
}
