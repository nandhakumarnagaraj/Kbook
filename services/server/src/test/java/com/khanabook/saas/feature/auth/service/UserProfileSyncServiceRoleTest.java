package com.khanabook.saas.feature.auth.service;

import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Account creation over the sync channel is an owner capability.
 *
 * <p>/sync/config/users/push stays open to SHOP_STAFF because a staff terminal
 * legitimately calls it to mirror its own profile row. The protection therefore has
 * to live in the role enforcement, not the endpoint gate: a non-owner must neither
 * mint a new account nor choose a role.
 *
 * <p>Regression cover for the case where only isKbookAdmin was consulted, so a
 * SHOP_STAFF token satisfied {@code !isKbookAdmin} and a fabricated new User row was
 * granted OWNER + isActive + cleared tokenInvalidatedAt — a full tenant takeover.
 */
class UserProfileSyncServiceRoleTest {

    private final UserProfileSyncService service = new UserProfileSyncService();

    @Test
    void shopStaffCannotCreateAccountViaSync() {
        User newUser = new User();
        newUser.setRole(UserRole.OWNER);
        newUser.setIsActive(false);

        boolean allowed = service.enforceNewUserRoleForCaller(newUser, false, UserRole.SHOP_STAFF.name());

        assertThat(allowed).isFalse();
    }

    @Test
    void ownerCreatingAccountGetsOwnerActiveWithNoInvalidation() {
        User newUser = new User();

        boolean allowed = service.enforceNewUserRoleForCaller(newUser, false, UserRole.OWNER.name());

        assertThat(allowed).isTrue();
        assertThat(newUser.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(newUser.getIsActive()).isTrue();
        assertThat(newUser.getTokenInvalidatedAt()).isNull();
    }

    @Test
    void kbookAdminMayCreateAnyRole() {
        User newUser = new User();
        newUser.setRole(UserRole.KBOOK_ADMIN);

        boolean allowed = service.enforceNewUserRoleForCaller(newUser, true, UserRole.KBOOK_ADMIN.name());

        assertThat(allowed).isTrue();
        assertThat(newUser.getRole()).isEqualTo(UserRole.KBOOK_ADMIN);
    }

    /** A missing/blank role must fail closed, not fall through to the owner branch. */
    @Test
    void nullRoleCannotCreateAccount() {
        User newUser = new User();

        assertThat(service.enforceNewUserRoleForCaller(newUser, false, null)).isFalse();
        assertThat(service.enforceNewUserRoleForCaller(newUser, false, "")).isFalse();
    }

    /**
     * A rejected record must not be mutated into a persistable account. The User
     * entity defaults role=OWNER and isActive=true, so the meaningful assertion is
     * that the guard did not perform its OWNER-branch mutations — in particular it
     * must not clear tokenInvalidatedAt, which is what makes a deactivated staff
     * session usable again. The caller quarantines on a false return, so the row is
     * never written regardless of these field values.
     */
    @Test
    void rejectedStaffRecordIsNotMutatedIntoAValidAccount() {
        User newUser = new User();
        newUser.setTokenInvalidatedAt(999L);

        boolean allowed = service.enforceNewUserRoleForCaller(newUser, false, UserRole.SHOP_STAFF.name());

        assertThat(allowed).isFalse();
        // OWNER branch would have nulled this out, reactivating the session.
        assertThat(newUser.getTokenInvalidatedAt()).isEqualTo(999L);
    }
}
