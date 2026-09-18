package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
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
    private com.khanabook.saas.service.PasswordResetOtpService passwordResetOtpService;
    private BusinessWriteService service;

    private void setupService() {
        userRepository = mock(UserRepository.class);
        MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
        RestaurantTerminalRepository terminalRepository = mock(RestaurantTerminalRepository.class);
        RestaurantProfileRepository profileRepository = mock(RestaurantProfileRepository.class);
        passwordResetOtpService = mock(com.khanabook.saas.service.PasswordResetOtpService.class);
        service = new BusinessWriteService(userRepository, mock(CategoryRepository.class), menuItemRepository,
                terminalRepository, profileRepository, mock(com.khanabook.saas.service.PermissionService.class),
                passwordResetOtpService);
        // Tombstone-aware staff create: only a LIVE account blocks the phone, and any
        // soft-deleted rows holding it are released first. Default to "available".
        when(userRepository.findActiveByAnyIdentifier(anyString())).thenReturn(java.util.Optional.empty());
        when(userRepository.findDeletedHoldingIdentifier(anyString())).thenReturn(java.util.List.of());
    }

    // ─── Property 2: Staff Creation Produces Valid User ──────────────────────────

    /**
     * Property 2: For any valid CreateStaffRequest (name non-empty, phone exactly 10 digits,
     * role in {OWNER, SHOP_STAFF}), the service SHALL create a user and return a response
     * containing a non-null temporary password and the assigned userId.
     *
     * Validates: Requirements 2.2
     */
    @Property(tries = 20)
    @Label("Property 2: Valid staff request produces user with OTP sent and userId")
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

        CreateStaffRequest request = new CreateStaffRequest(name, phone, role, null, null);
        StaffCreatedResponse response = service.createStaff(1L, request);

        assertNotNull(response.userId(), "userId must not be null");
        assertTrue(response.otpSent(), "otpSent must be true on successful onboarding");
        assertEquals(name, response.name());
        assertEquals(phone, response.phone());
        assertEquals(role.toUpperCase(), response.role());

        verify(userRepository).save(any(User.class));
        verify(passwordResetOtpService).issueOtp(phone);
    }

    // ─── Property 3: Staff Input Validation ──────────────────────────────────────

    /**
     * Property 3: For any CreateStaffRequest where role is not a valid assignable staff
     * role {OWNER, SHOP_STAFF} (or is KBOOK_ADMIN), the system
     * SHALL reject with an exception and leave the user table unchanged.
     *
     * Note: Phone format validation (exactly 10 digits) is enforced by Jakarta @Pattern
     * on the DTO at the controller layer. Role validation is enforced by the service's
     * parseRole() method.
     *
     * Validates: Requirements 2.5, 2.6
     */
    @Property(tries = 20)
    @Label("Property 3: Invalid role causes rejection, user table unchanged")
    void invalidRoleRejected(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone,
            @ForAll("invalidRoles") String role
    ) {
        setupService();

        when(userRepository.existsByPhoneNumber(phone)).thenReturn(false);
        when(userRepository.existsByLoginId(phone)).thenReturn(false);

        CreateStaffRequest request = new CreateStaffRequest(name, phone, role, null, null);

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
    @Property(tries = 20)
    @Label("Property 4: Duplicate phone number causes rejection, user table unchanged")
    void duplicatePhoneRejected(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone,
            @ForAll("validRoles") String role
    ) {
        setupService();

        // A LIVE account already holds this identifier.
        when(userRepository.findActiveByAnyIdentifier(phone)).thenReturn(java.util.Optional.of(new User()));

        CreateStaffRequest request = new CreateStaffRequest(name, phone, role, null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createStaff(1L, request),
                "Duplicate phone should cause rejection");

        assertTrue(ex.getMessage().toLowerCase().contains("phone") ||
                   ex.getMessage().toLowerCase().contains("already exists"),
                "Error message should reference phone/duplicate: " + ex.getMessage());

        verify(userRepository, never()).save(any(User.class));
    }

    @Property(tries = 20)
    @Label("Property 4: Duplicate loginId causes rejection, user table unchanged")
    void duplicateLoginIdRejected(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone,
            @ForAll("validRoles") String role
    ) {
        setupService();

        // findActiveByAnyIdentifier matches on phone OR loginId OR email OR whatsapp,
        // so a live account holding the loginId is surfaced by the same lookup.
        when(userRepository.findActiveByAnyIdentifier(phone)).thenReturn(java.util.Optional.of(new User()));

        CreateStaffRequest request = new CreateStaffRequest(name, phone, role, null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createStaff(1L, request),
                "Duplicate loginId (phone) should cause rejection");

        assertTrue(ex.getMessage().toLowerCase().contains("phone") ||
                   ex.getMessage().toLowerCase().contains("already exists"),
                "Error message should reference phone/duplicate: " + ex.getMessage());

        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * Soft-deleted staff must not reserve a phone number forever. Re-adding a
     * previously-removed staff member SHALL succeed, and the identifier SHALL be
     * released from the soft-deleted row(s) so the partial unique indexes do not
     * block the insert. Mirrors signup's releaseIdentifierFromDeletedUsers.
     */
    @Property(tries = 10)
    @Label("Soft-deleted staff phone can be re-added; tombstoned identifier is released")
    void softDeletedPhoneCanBeReAdded(
            @ForAll("validNames") String name,
            @ForAll("validPhones") String phone
    ) {
        setupService();

        // No LIVE account holds the number, but a soft-deleted row still does.
        when(userRepository.findActiveByAnyIdentifier(phone)).thenReturn(java.util.Optional.empty());
        User dead = new User();
        dead.setId(77L);
        dead.setIsDeleted(true);
        dead.setPhoneNumber(phone);
        dead.setWhatsappNumber(phone);
        dead.setLoginId(phone);
        when(userRepository.findDeletedHoldingIdentifier(phone)).thenReturn(java.util.List.of(dead));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(42L);
            return u;
        });

        CreateStaffRequest request = new CreateStaffRequest(name, phone, "SHOP_STAFF", null, null);
        StaffCreatedResponse response = service.createStaff(1L, request);

        assertNotNull(response.userId(), "Re-adding a removed staff phone must succeed");

        // The tombstoned row released its reusable identifiers; history is preserved.
        verify(userRepository).saveAll(any());
        assertNull(dead.getPhoneNumber(), "phoneNumber must be detached from the deleted row");
        assertNull(dead.getWhatsappNumber(), "whatsappNumber must be detached from the deleted row");
        assertTrue(dead.getLoginId().contains("deleted:77"), "loginId must be tombstoned, was: " + dead.getLoginId());
        assertTrue(dead.getIsDeleted(), "the deleted row must remain soft-deleted");
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
        return Arbitraries.of("OWNER", "SHOP_STAFF");
    }

    @Provide
    Arbitrary<String> invalidRoles() {
        return Arbitraries.oneOf(
                Arbitraries.just("KBOOK_ADMIN"),
                Arbitraries.just("INVALID_ROLE"),
                Arbitraries.just("admin"),
                Arbitraries.just("user"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                        .filter(s -> !s.equalsIgnoreCase("OWNER") && !s.equalsIgnoreCase("SHOP_STAFF")
                                && !s.equalsIgnoreCase("KBOOK_ADMIN"))
        );
    }
}
