package com.khanabook.saas.security;

import com.khanabook.saas.entity.UserRole;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: web-admin-full-capability, Property 1: Role-Based Access Control
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.6
 *
 * For any (role, operation) pair where operation is one of the defined write operations
 * (staff CRUD, menu CRUD, terminal reactivation, business suspend/activate),
 * access SHALL be granted if and only if the role is contained in that operation's
 * allowed roles set. Any other role SHALL receive AccessDeniedException (HTTP 403).
 */
class RoleBasedAccessControlProperties {

    /**
     * Represents a protected operation with its allowed roles.
     */
    enum ProtectedOperation {
        // Staff CRUD endpoints: OWNER only (Requirement 1.1)
        CREATE_STAFF(Set.of(UserRole.OWNER)),
        UPDATE_STAFF(Set.of(UserRole.OWNER)),
        DEACTIVATE_STAFF(Set.of(UserRole.OWNER)),

        // Menu CRUD endpoints: OWNER only (Requirement 1.4)
        CREATE_MENU_ITEM(Set.of(UserRole.OWNER)),
        UPDATE_MENU_ITEM(Set.of(UserRole.OWNER)),
        DELETE_MENU_ITEM(Set.of(UserRole.OWNER)),
        TOGGLE_MENU_AVAILABILITY(Set.of(UserRole.OWNER)),

        // Terminal reactivation: OWNER or SHOP_ADMIN (Requirement 1.3)
        REACTIVATE_TERMINAL(Set.of(UserRole.OWNER, UserRole.SHOP_ADMIN)),

        // Business suspend/activate: KBOOK_ADMIN only (Requirement 1.2)
        SUSPEND_BUSINESS(Set.of(UserRole.KBOOK_ADMIN)),
        ACTIVATE_BUSINESS(Set.of(UserRole.KBOOK_ADMIN));

        private final Set<UserRole> allowedRoles;

        ProtectedOperation(Set<UserRole> allowedRoles) {
            this.allowedRoles = allowedRoles;
        }

        public Set<UserRole> getAllowedRoles() {
            return allowedRoles;
        }

        public UserRole[] getAllowedRolesArray() {
            return allowedRoles.toArray(new UserRole[0]);
        }
    }

    private final RequireRoleAspect aspect = new RequireRoleAspect();

    /**
     * Property: For any (role, operation) pair, access is granted iff role is in allowed set.
     *
     * Generates random combinations of UserRole and ProtectedOperation,
     * then verifies the RequireRoleAspect grants or denies access correctly.
     */
    @Property(tries = 200)
    @Label("Access granted iff role is in operation's allowed roles set, else AccessDeniedException")
    void accessGrantedIffRoleInAllowedSet(
            @ForAll("roles") UserRole role,
            @ForAll("operations") ProtectedOperation operation
    ) throws Throwable {
        // Setup: configure TenantContext with the given role
        TenantContext.setCurrentRole(role.name());
        TenantContext.setCurrentTenant(1L);
        TenantContext.setCurrentUserId(1L);

        try {
            // Create a mock @RequireRole annotation for this operation
            RequireRole requireRole = mockRequireRole(operation.getAllowedRolesArray());

            // Create a mock join point
            ProceedingJoinPoint joinPoint = mockJoinPoint(operation.name());

            boolean shouldBeAllowed = operation.getAllowedRoles().contains(role);

            if (shouldBeAllowed) {
                // Access should be granted — aspect should call proceed() and return its value
                Object result = aspect.enforce(joinPoint, requireRole);
                assertEquals("OK", result, 
                    String.format("Role %s should be allowed for %s", role, operation));
            } else {
                // Access should be denied — aspect should throw AccessDeniedException
                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                    () -> aspect.enforce(joinPoint, requireRole),
                    String.format("Role %s should be denied for %s", role, operation));
                assertNotNull(ex.getMessage());
            }
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Property: Null role always results in AccessDeniedException for any operation.
     *
     * Validates: Requirement 1.6 — if a request reaches the server without the required role,
     * it SHALL return HTTP 403.
     */
    @Property(tries = 100)
    @Label("Null role always denied for any operation")
    void nullRoleAlwaysDenied(
            @ForAll("operations") ProtectedOperation operation
    ) throws Throwable {
        // Setup: no role set (simulates missing/invalid JWT)
        TenantContext.setCurrentRole(null);

        try {
            RequireRole requireRole = mockRequireRole(operation.getAllowedRolesArray());
            ProceedingJoinPoint joinPoint = mockJoinPoint(operation.name());

            assertThrows(AccessDeniedException.class,
                () -> aspect.enforce(joinPoint, requireRole),
                String.format("Null role should be denied for %s", operation));
        } finally {
            TenantContext.clear();
        }
    }

    // ─── Generators ─────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<UserRole> roles() {
        return Arbitraries.of(UserRole.class);
    }

    @Provide
    Arbitrary<ProtectedOperation> operations() {
        return Arbitraries.of(ProtectedOperation.class);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private RequireRole mockRequireRole(UserRole[] allowedRoles) {
        RequireRole requireRole = mock(RequireRole.class);
        when(requireRole.value()).thenReturn(allowedRoles);
        return requireRole;
    }

    private ProceedingJoinPoint mockJoinPoint(String methodName) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn("OK");
        return joinPoint;
    }
}
