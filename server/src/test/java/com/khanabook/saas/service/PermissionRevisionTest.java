package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.StaffPermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 1 of distributed-authorization: verifies the monotonic permission revision
 * and per-key last_revoked_revision marker. Enforcement (sync revalidation) is Step 2.
 */
class PermissionRevisionTest extends BaseIntegrationTest {

    @Autowired private PermissionService permissionService;
    @Autowired private StaffPermissionRepository permissionRepo;

    private static final String KEY = "billing.settle";

    private Long seedStaff(Long restaurantId) {
        // OWNER for the restaurant, plus a staff (SHOP_ADMIN) whose permissions we mutate.
        persistUser("owner" + System.currentTimeMillis() + "@kbook.com", restaurantId, UserRole.OWNER);
        var staff = persistUser("staff" + System.currentTimeMillis() + "@kbook.com", restaurantId, UserRole.SHOP_ADMIN);
        return staff.getId();
    }

    @Transactional
    @Test
    void grantThenRevokeIncrementsRevisionAndStampsRevocation() {
        Long r = 300L + System.currentTimeMillis() % 100;
        Long staff = seedStaff(r);

        long rev0 = permissionService.getPermissionRevision(r, staff);

        permissionService.grantPermission(r, staff, KEY, 1L);
        long revAfterGrant = permissionService.getPermissionRevision(r, staff);
        assertTrue(revAfterGrant > rev0, "grant must bump revision");

        permissionService.revokePermission(r, staff, KEY);
        long revAfterRevoke = permissionService.getPermissionRevision(r, staff);
        assertTrue(revAfterRevoke > revAfterGrant, "revoke must bump revision");

        var perm = permissionRepo.findByRestaurantIdAndUserIdAndPermissionKey(r, staff, KEY).orElseThrow();
        assertFalse(perm.getGranted());
        assertNotNull(perm.getLastRevokedRevision(), "revoke must stamp last_revoked_revision");
        assertEquals(revAfterRevoke, perm.getLastRevokedRevision(),
                "last_revoked_revision must equal the revision at revoke time");
    }

    @Transactional
    @Test
    void grantRevokeGrantRevokeIsMonotonic() {
        Long r = 400L + System.currentTimeMillis() % 100;
        Long staff = seedStaff(r);

        permissionService.grantPermission(r, staff, KEY, 1L);
        long r1 = permissionService.getPermissionRevision(r, staff);
        permissionService.revokePermission(r, staff, KEY);
        long r2 = permissionService.getPermissionRevision(r, staff);
        permissionService.grantPermission(r, staff, KEY, 1L);
        long r3 = permissionService.getPermissionRevision(r, staff);
        permissionService.revokePermission(r, staff, KEY);
        long r4 = permissionService.getPermissionRevision(r, staff);

        assertTrue(r1 < r2 && r2 < r3 && r3 < r4, "revision must be strictly monotonic across grant/revoke cycles");

        // The most recent revocation revision is the latest one (r4), so an op created
        // before r4 would be rejected under Decision A strict.
        var perm = permissionRepo.findByRestaurantIdAndUserIdAndPermissionKey(r, staff, KEY).orElseThrow();
        assertEquals(r4, perm.getLastRevokedRevision());
    }

    @Transactional
    @Test
    void redundantGrantDoesNotBumpRevision() {
        Long r = 500L + System.currentTimeMillis() % 100;
        Long staff = seedStaff(r);

        permissionService.grantPermission(r, staff, KEY, 1L);
        long r1 = permissionService.getPermissionRevision(r, staff);
        permissionService.grantPermission(r, staff, KEY, 1L); // already granted → no state change
        long r2 = permissionService.getPermissionRevision(r, staff);

        assertEquals(r1, r2, "re-granting an already-granted permission must not bump the revision");
    }
}
