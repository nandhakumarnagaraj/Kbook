package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.webadmin.dto.UpdateStaffRequest;
import net.jqwik.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: web-admin-full-capability, Property 5: Staff Edit Preserves Integrity
 * Feature: web-admin-full-capability, Property 6: Staff Deactivation Invalidates Sessions
 *
 * Validates: Requirements 3.2, 4.2
 */
class StaffEditProperties {

    private UserRepository userRepository;
    private BusinessWriteService service;

    private void setupService() {
        userRepository = mock(UserRepository.class);
        MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
        RestaurantTerminalRepository terminalRepository = mock(RestaurantTerminalRepository.class);
        RestaurantProfileRepository profileRepository = mock(RestaurantProfileRepository.class);
        service = new BusinessWriteService(userRepository, mock(CategoryRepository.class), menuItemRepository,
                terminalRepository, profileRepository);
    }

    // ─── Property 5: Staff Edit Preserves Integrity ──────────────────────────────

    /**
     * Property 5: For any valid UpdateStaffRequest targeting an existing staff member,
     * the service SHALL update exactly the specified fields and leave all other staff
     * members unchanged.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Label("Property 5: Staff edit updates only specified fields on target user")
    void staffEditUpdatesOnlyTargetFields(
            @ForAll("validNames") String newName,
            @ForAll("validPhones") String newPhone,
            @ForAll("validRoles") String newRole,
            @ForAll("optionalEmails") String newEmail
    ) {
        setupService();

        Long restaurantId = 1L;
        Long targetUserId = 42L;

        User existingUser = createExistingUser(targetUserId, restaurantId, "OriginalName",
                "9876543210", UserRole.SHOP_ADMIN, "old@example.com");

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateStaffRequest request = new UpdateStaffRequest(newName, newPhone, newEmail, newRole);
        service.updateStaff(restaurantId, targetUserId, request);

        // Verify the target user's fields are updated
        assertEquals(newName, existingUser.getName(), "Name should be updated");
        assertEquals(newPhone, existingUser.getPhoneNumber(), "Phone should be updated");
        assertEquals(newPhone, existingUser.getLoginId(), "LoginId should match phone");
        assertEquals(newPhone, existingUser.getWhatsappNumber(), "WhatsApp number should match phone");
        assertEquals(newEmail, existingUser.getEmail(), "Email should be updated");
        assertEquals(UserRole.valueOf(newRole.toUpperCase()), existingUser.getRole(), "Role should be updated");

        // Verify unchanged fields
        assertEquals(restaurantId, existingUser.getRestaurantId(), "RestaurantId must not change");
        assertEquals(targetUserId, existingUser.getId(), "UserId must not change");
        assertTrue(existingUser.getIsActive(), "isActive must not change during edit");
        assertFalse(existingUser.getIsDeleted(), "isDeleted must not change during edit");

        // Verify save was called exactly once (only target user persisted)
        verify(userRepository, times(1)).save(existingUser);
    }

    /**
     * Property 5 (role change variant): When role changes, tokenInvalidatedAt is set;
     * when role stays same, tokenInvalidatedAt is not modified.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Label("Property 5: Role change sets tokenInvalidatedAt, same role leaves it unchanged")
    void roleChangeInvalidatesTokenCorrectly(
            @ForAll("validNames") String newName,
            @ForAll("validPhones") String newPhone,
            @ForAll("validRoles") String currentRole,
            @ForAll("validRoles") String newRole
    ) {
        setupService();

        Long restaurantId = 1L;
        Long targetUserId = 42L;
        Long originalTokenInvalidatedAt = 1000L;

        User existingUser = createExistingUser(targetUserId, restaurantId, "OldName",
                "1234567890", UserRole.valueOf(currentRole.toUpperCase()), null);
        existingUser.setTokenInvalidatedAt(originalTokenInvalidatedAt);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateStaffRequest request = new UpdateStaffRequest(newName, newPhone, null, newRole);
        service.updateStaff(restaurantId, targetUserId, request);

        boolean roleChanged = !currentRole.equalsIgnoreCase(newRole);
        if (roleChanged) {
            assertNotNull(existingUser.getTokenInvalidatedAt(), "tokenInvalidatedAt must be set on role change");
            assertTrue(existingUser.getTokenInvalidatedAt() > originalTokenInvalidatedAt,
                    "tokenInvalidatedAt must be updated to a newer timestamp on role change");
        } else {
            assertEquals(originalTokenInvalidatedAt, existingUser.getTokenInvalidatedAt(),
                    "tokenInvalidatedAt must remain unchanged when role is not changed");
        }
    }

    // ─── Property 6: Staff Deactivation Invalidates Sessions ─────────────────────

    /**
     * Property 6: For any active staff member (not the calling user), deactivation SHALL
     * set isActive=false and update tokenInvalidatedAt to current time.
     *
     * Validates: Requirements 4.2
     */
    @Property(tries = 100)
    @Label("Property 6: Deactivation sets isActive=false and tokenInvalidatedAt to current time")
    void deactivationSetsInactiveAndInvalidatesToken(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone,
            @ForAll("validRoles") String role
    ) {
        setupService();

        Long restaurantId = 1L;
        Long targetUserId = 55L;

        User activeUser = createExistingUser(targetUserId, restaurantId, name, phone,
                UserRole.valueOf(role.toUpperCase()), null);
        activeUser.setIsActive(true);
        activeUser.setTokenInvalidatedAt(null); // No prior invalidation

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        long beforeDeactivation = System.currentTimeMillis();
        service.deactivateStaff(restaurantId, targetUserId);
        long afterDeactivation = System.currentTimeMillis();

        assertFalse(activeUser.getIsActive(), "isActive must be false after deactivation");
        assertNotNull(activeUser.getTokenInvalidatedAt(), "tokenInvalidatedAt must be set after deactivation");
        assertTrue(activeUser.getTokenInvalidatedAt() >= beforeDeactivation,
                "tokenInvalidatedAt must be >= time before deactivation call");
        assertTrue(activeUser.getTokenInvalidatedAt() <= afterDeactivation,
                "tokenInvalidatedAt must be <= time after deactivation call");

        // Verify the user was saved
        verify(userRepository, times(1)).save(activeUser);
    }

    /**
     * Property 6 (additional): Deactivation of a user in a different restaurant fails.
     *
     * Validates: Requirements 4.2
     */
    @Property(tries = 100)
    @Label("Property 6: Deactivation fails for user in different restaurant")
    void deactivationFailsForWrongRestaurant(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone
    ) {
        setupService();

        Long callerRestaurantId = 1L;
        Long targetUserId = 55L;
        Long otherRestaurantId = 99L;

        User userInOtherRestaurant = createExistingUser(targetUserId, otherRestaurantId, name, phone,
                UserRole.SHOP_ADMIN, null);
        userInOtherRestaurant.setIsActive(true);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(userInOtherRestaurant));

        assertThrows(IllegalArgumentException.class,
                () -> service.deactivateStaff(callerRestaurantId, targetUserId),
                "Deactivation should fail for user in different restaurant");

        // User should remain unchanged
        assertTrue(userInOtherRestaurant.getIsActive(), "isActive must remain true");
        verify(userRepository, never()).save(any(User.class));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private User createExistingUser(Long id, Long restaurantId, String name, String phone,
                                    UserRole role, String email) {
        User user = new User();
        user.setId(id);
        user.setRestaurantId(restaurantId);
        user.setName(name);
        user.setPhoneNumber(phone);
        user.setLoginId(phone);
        user.setWhatsappNumber(phone);
        user.setEmail(email);
        user.setRole(role);
        user.setIsActive(true);
        user.setIsDeleted(false);
        user.setAuthProvider(AuthProvider.PHONE);
        user.setPasswordHash("$2a$10$hashedpassword");
        user.setDeviceId("web-admin");
        user.setLocalId(System.currentTimeMillis());
        user.setCreatedAt(System.currentTimeMillis());
        user.setUpdatedAt(System.currentTimeMillis());
        user.setVersion(0L);
        return user;
    }

    // ─── Generators ─────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> validPhones() {
        return Arbitraries.strings()
                .numeric()
                .ofLength(10);
    }

    @Provide
    Arbitrary<String> validRoles() {
        return Arbitraries.of("OWNER", "SHOP_ADMIN");
    }

    @Provide
    Arbitrary<String> optionalEmails() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings()
                        .alpha()
                        .ofMinLength(3)
                        .ofMaxLength(15)
                        .map(s -> s.toLowerCase() + "@test.com")
        );
    }
}
