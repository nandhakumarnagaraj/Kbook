package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.webadmin.dto.CreateStaffRequest;
import com.khanabook.saas.webadmin.dto.StaffCreatedResponse;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: web-admin-full-capability, Property 2: Staff Creation Produces Valid User
 * Feature: web-admin-full-capability, Property 3: Staff Input Validation
 * Feature: web-admin-full-capability, Property 4: Duplicate Phone Rejection
 *
 * Validates: Requirements 2.2, 2.4, 2.5, 2.6
 */
class StaffCreationProperties {

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

    // ─── Property 2: Staff Creation Produces Valid User ──────────────────────────

    /**
     * Property 2: For any valid CreateStaffRequest (name non-empty, phone exactly 10 digits,
     * role in {OWNER, SHOP_ADMIN}), the service SHALL create a user and return a response
     * containing a non-null temporary password and the assigned userId.
     *
     * Validates: Requirements 2.2
     */
    @Property(tries = 100)
    @Label("Property 2: Valid staff request produces user with non-null tempPassword and userId")
    void validStaffRequestProducesValidUser(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone,
            @ForAll("validRoles") String role
    ) {
        setupService();

        when(userRepository.existsByPhoneNumber(phone)).thenReturn(false);
        when(userRepository.existsByLoginId(phone)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        CreateStaffRequest request = new CreateStaffRequest(name, phone, role, null);
        StaffCreatedResponse response = service.createStaff(1L, request);

        assertNotNull(response.userId(), "userId must not be null");
        assertNotNull(response.temporaryPassword(), "temporaryPassword must not be null");
        assertFalse(response.temporaryPassword().isEmpty(), "temporaryPassword must not be empty");
        assertEquals(name, response.name());
        assertEquals(phone, response.phone());
        assertEquals(role.toUpperCase(), response.role());

        verify(userRepository).save(any(User.class));
    }

    // ─── Property 3: Staff Input Validation ──────────────────────────────────────

    /**
     * Property 3: For any CreateStaffRequest where role is not in {OWNER, SHOP_ADMIN},
     * the system SHALL reject with an exception and leave the user table unchanged.
     *
     * Note: Phone format validation (exactly 10 digits) is enforced by Jakarta @Pattern
     * on the DTO at the controller layer. Role validation is enforced by the service's
     * parseRole() method.
     *
     * Validates: Requirements 2.5, 2.6
     */
    @Property(tries = 100)
    @Label("Property 3: Invalid role causes rejection, user table unchanged")
    void invalidRoleRejected(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone,
            @ForAll("invalidRoles") String role
    ) {
        setupService();

        when(userRepository.existsByPhoneNumber(phone)).thenReturn(false);
        when(userRepository.existsByLoginId(phone)).thenReturn(false);

        CreateStaffRequest request = new CreateStaffRequest(name, phone, role, null);

        assertThrows(IllegalArgumentException.class, () -> service.createStaff(1L, request),
                "Invalid role '" + role + "' should cause rejection");

        verify(userRepository, never()).save(any(User.class));
    }

    // ─── Property 4: Duplicate Phone Rejection ───────────────────────────────────

    /**
     * Property 4: For any phone number that already exists in the users table for the same
     * restaurant, creating a staff member with that phone SHALL fail with a duplicate error
     * and leave the user table unchanged.
     *
     * Validates: Requirements 2.4
     */
    @Property(tries = 100)
    @Label("Property 4: Duplicate phone number causes rejection, user table unchanged")
    void duplicatePhoneRejected(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone,
            @ForAll("validRoles") String role
    ) {
        setupService();

        when(userRepository.existsByPhoneNumber(phone)).thenReturn(true);

        CreateStaffRequest request = new CreateStaffRequest(name, phone, role, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createStaff(1L, request),
                "Duplicate phone should cause rejection");

        assertTrue(ex.getMessage().toLowerCase().contains("phone") ||
                   ex.getMessage().toLowerCase().contains("already exists"),
                "Error message should reference phone/duplicate: " + ex.getMessage());

        verify(userRepository, never()).save(any(User.class));
    }

    @Property(tries = 100)
    @Label("Property 4: Duplicate loginId causes rejection, user table unchanged")
    void duplicateLoginIdRejected(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone,
            @ForAll("validRoles") String role
    ) {
        setupService();

        when(userRepository.existsByPhoneNumber(phone)).thenReturn(false);
        when(userRepository.existsByLoginId(phone)).thenReturn(true);

        CreateStaffRequest request = new CreateStaffRequest(name, phone, role, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createStaff(1L, request),
                "Duplicate loginId (phone) should cause rejection");

        assertTrue(ex.getMessage().toLowerCase().contains("phone") ||
                   ex.getMessage().toLowerCase().contains("already exists"),
                "Error message should reference phone/duplicate: " + ex.getMessage());

        verify(userRepository, never()).save(any(User.class));
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
    Arbitrary<String> invalidRoles() {
        return Arbitraries.oneOf(
                Arbitraries.just("KBOOK_ADMIN"),
                Arbitraries.just("INVALID_ROLE"),
                Arbitraries.just("admin"),
                Arbitraries.just("user"),
                Arbitraries.just("manager"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                        .filter(s -> !s.equalsIgnoreCase("OWNER") && !s.equalsIgnoreCase("SHOP_ADMIN"))
        );
    }
}
