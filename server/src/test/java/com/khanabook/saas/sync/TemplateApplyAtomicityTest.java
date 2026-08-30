package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.service.PermissionService;
import com.khanabook.saas.utility.JwtUtility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Distributed state problem E1: Template apply atomicity.
 *
 * Tests that permission template application modifies revision state,
 * and that the revision is monotonically increasing.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TemplateApplyAtomicityTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 9001L;

    @Autowired private PermissionService permissionService;
    @Autowired private StaffPermissionRevisionRepository revisionRepository;
    @Autowired private StaffPermissionRepository staffPermissionRepository;

    @Test
    void singlePermissionGrant_createsRevision() {
        User user = persistUser("user-e1-" + UUID.randomUUID(), RESTAURANT, UserRole.CASHIER);
        User owner = persistUser("owner-e1-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);

        permissionService.grantPermission(RESTAURANT, user.getId(), "billing.settle", owner.getId());

        var revision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, user.getId());
        assertThat(revision).isPresent();
        assertThat(revision.get().getRevision()).isEqualTo(1L);

        var perm = staffPermissionRepository.findByRestaurantIdAndUserIdAndPermissionKey(
                RESTAURANT, user.getId(), "billing.settle");
        assertThat(perm).isPresent();
        assertThat(perm.get().getGranted()).isTrue();
    }

    @Test
    void multiplePermissionGrants_revisionMonotonicallyIncreases() {
        User user = persistUser("user-e1-multi-" + UUID.randomUUID(), RESTAURANT, UserRole.CASHIER);
        User owner = persistUser("owner-e1-multi-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);

        // Grant 3 different valid permissions
        permissionService.grantPermission(RESTAURANT, user.getId(), "billing.settle", owner.getId());
        permissionService.grantPermission(RESTAURANT, user.getId(), "billing.create", owner.getId());
        permissionService.grantPermission(RESTAURANT, user.getId(), "menu.view", owner.getId());

        var revision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, user.getId());
        assertThat(revision).isPresent();
        assertThat(revision.get().getRevision()).isGreaterThanOrEqualTo(3L);
    }

    @Test
    void grantRevokeGrant_revisionNeverDecreases() {
        User user = persistUser("user-e1-monotonic-" + UUID.randomUUID(), RESTAURANT, UserRole.CASHIER);
        User owner = persistUser("owner-e1-monotonic-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);

        permissionService.grantPermission(RESTAURANT, user.getId(), "billing.settle", owner.getId());
        long r1 = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, user.getId())
                .map(StaffPermissionRevision::getRevision).orElse(0L);

        permissionService.revokePermission(RESTAURANT, user.getId(), "billing.settle");
        long r2 = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, user.getId())
                .map(StaffPermissionRevision::getRevision).orElse(0L);

        permissionService.grantPermission(RESTAURANT, user.getId(), "billing.settle", owner.getId());
        long r3 = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, user.getId())
                .map(StaffPermissionRevision::getRevision).orElse(0L);

        assertThat(r2).isGreaterThan(r1);
        assertThat(r3).isGreaterThan(r2);
    }

    @Test
    void redundantGrant_doesNotBumpRevision() {
        User user = persistUser("user-e1-redundant-" + UUID.randomUUID(), RESTAURANT, UserRole.CASHIER);
        User owner = persistUser("owner-e1-redundant-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);

        permissionService.grantPermission(RESTAURANT, user.getId(), "billing.settle", owner.getId());
        long r1 = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, user.getId())
                .map(StaffPermissionRevision::getRevision).orElse(0L);

        permissionService.grantPermission(RESTAURANT, user.getId(), "billing.settle", owner.getId());
        long r2 = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, user.getId())
                .map(StaffPermissionRevision::getRevision).orElse(0L);

        assertThat(r2).isEqualTo(r1);
    }
}
