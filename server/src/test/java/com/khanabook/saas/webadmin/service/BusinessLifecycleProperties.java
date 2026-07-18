package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.controller.AuthController.LoginRequest;
import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.exception.BusinessSuspendedException;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.service.impl.AuthServiceImpl;
import com.khanabook.saas.utility.JwtUtility;
import net.jqwik.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: web-admin-full-capability, Property 17: Business Suspend/Activate Round-Trip
 * Feature: web-admin-full-capability, Property 18: Suspended Business Blocks Login
 *
 * Validates: Requirements 14.3, 14.4, 14.6
 */
class BusinessLifecycleProperties {

    private RestaurantProfileRepository profileRepository;
    private AdminWriteService adminWriteService;

    private UserRepository userRepository;
    private JwtUtility jwtUtility;
    private PasswordEncoder passwordEncoder;
    private com.khanabook.saas.service.PasswordResetOtpService passwordResetOtpService;
    private AuthServiceImpl authService;

    private void setupAdminWriteService() {
        profileRepository = mock(RestaurantProfileRepository.class);
        adminWriteService = new AdminWriteService(profileRepository);
    }

    private void setupAuthService() {
        userRepository = mock(UserRepository.class);
        profileRepository = mock(RestaurantProfileRepository.class);
        jwtUtility = mock(JwtUtility.class);
        passwordEncoder = mock(PasswordEncoder.class);
        passwordResetOtpService = mock(com.khanabook.saas.service.PasswordResetOtpService.class);
        authService = new AuthServiceImpl(userRepository, profileRepository, jwtUtility, passwordEncoder, passwordResetOtpService);
    }

    // ─── Property 17: Business Suspend/Activate Round-Trip ──────────────────────

    /**
     * Property 17: For any active business, suspending then activating SHALL restore
     * it to active status (isSuspended=false).
     *
     * Validates: Requirements 14.3, 14.4
     */
    @Property(tries = 100)
    @Label("Property 17: Suspend then activate restores active status")
    void suspendThenActivateRestoresActiveStatus(
            @ForAll("restaurantIds") Long restaurantId
    ) {
        setupAdminWriteService();

        RestaurantProfile profile = createProfile(restaurantId, false);

        when(profileRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(RestaurantProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // Suspend the business
        adminWriteService.suspendBusiness(restaurantId);
        assertTrue(profile.getIsSuspended(), "Business must be suspended after suspendBusiness()");

        // Activate the business
        adminWriteService.activateBusiness(restaurantId);
        assertFalse(profile.getIsSuspended(), "Business must be active after activateBusiness()");

        verify(profileRepository, times(2)).save(profile);
    }

    /**
     * Property 17 (converse): For any suspended business, activating it SHALL set
     * isSuspended=false.
     *
     * Validates: Requirements 14.4
     */
    @Property(tries = 100)
    @Label("Property 17: Activating a suspended business sets isSuspended=false")
    void activateSuspendedBusinessSetsActive(
            @ForAll("restaurantIds") Long restaurantId
    ) {
        setupAdminWriteService();

        RestaurantProfile profile = createProfile(restaurantId, true);

        when(profileRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(RestaurantProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        adminWriteService.activateBusiness(restaurantId);

        assertFalse(profile.getIsSuspended(), "isSuspended must be false after activation");
        verify(profileRepository).save(profile);
    }

    // ─── Property 18: Suspended Business Blocks Login ───────────────────────────

    /**
     * Property 18: For any staff member belonging to a suspended business,
     * login attempts SHALL be rejected with BusinessSuspendedException.
     *
     * Validates: Requirements 14.6
     */
    @Property(tries = 100)
    @Label("Property 18: Login rejected with BusinessSuspendedException for suspended business")
    void suspendedBusinessBlocksLogin(
            @ForAll("restaurantIds") Long restaurantId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("passwords") String password
    ) {
        setupAuthService();

        User user = createUser(restaurantId, phone, password);
        RestaurantProfile profile = createProfile(restaurantId, true);

        when(userRepository.findByLoginIdIgnoreCase(phone)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashed_" + password)).thenReturn(true);
        when(profileRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.of(profile));

        LoginRequest request = new LoginRequest();
        request.setLoginId(phone);
        request.setPassword(password);
        request.setDeviceId("test-device");

        assertThrows(BusinessSuspendedException.class, () -> authService.login(request),
                "Login must be rejected with BusinessSuspendedException for suspended business");
    }

    // ─── Helper Methods ─────────────────────────────────────────────────────────

    private RestaurantProfile createProfile(Long restaurantId, boolean isSuspended) {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setId(restaurantId);
        profile.setRestaurantId(restaurantId);
        profile.setShopName("Test Restaurant");
        profile.setIsSuspended(isSuspended);
        profile.setUpdatedAt(System.currentTimeMillis());
        profile.setCreatedAt(System.currentTimeMillis());
        profile.setLocalId(1L);
        profile.setDeviceId("test-device");
        profile.setVersion(0L);
        profile.setIsDeleted(false);
        profile.setServerUpdatedAt(System.currentTimeMillis());
        return profile;
    }

    private User createUser(Long restaurantId, String phone, String password) {
        User user = new User();
        user.setId(1L);
        user.setRestaurantId(restaurantId);
        user.setName("Test User");
        user.setLoginId(phone);
        user.setPhoneNumber(phone);
        user.setEmail(null);
        user.setWhatsappNumber(phone);
        user.setPasswordHash("hashed_" + password);
        user.setRole(UserRole.OWNER);
        user.setIsActive(true);
        user.setAuthProvider(AuthProvider.PHONE);
        user.setLocalId(1L);
        user.setDeviceId("test-device");
        user.setVersion(0L);
        user.setIsDeleted(false);
        user.setUpdatedAt(System.currentTimeMillis());
        user.setServerUpdatedAt(System.currentTimeMillis());
        user.setCreatedAt(System.currentTimeMillis());
        return user;
    }

    // ─── Generators ─────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Long> restaurantIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<String> phoneNumbers() {
        return Arbitraries.strings()
                .numeric()
                .ofLength(10);
    }

    @Provide
    Arbitrary<String> passwords() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(6)
                .ofMaxLength(20);
    }
}
