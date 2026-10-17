package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.data.ItemRecipe;
import com.khanabook.saas.feature.menu.data.MenuExtractionJob;
import com.khanabook.saas.feature.inventory.data.RawMaterial;
import com.khanabook.saas.feature.inventory.data.PurchaseOrder;
import com.khanabook.saas.feature.inventory.data.PurchaseOrderItem;
import com.khanabook.saas.feature.inventory.data.StockMovement;
import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.inventory.data.Vendor;
import com.khanabook.saas.feature.inventory.data.CustomerProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzPayout;
import com.khanabook.saas.feature.payments.data.Chargeback;
import com.khanabook.saas.feature.notifications.data.NotificationEvent;
import com.khanabook.saas.feature.notifications.data.DeviceToken;
import com.khanabook.saas.feature.compliance.data.FssaiTracker;
import com.khanabook.saas.feature.compliance.data.FssaiRenewal;
import com.khanabook.saas.feature.staff.data.StaffPermission;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevision;
import com.khanabook.saas.feature.staff.data.PermissionKey;
import com.khanabook.saas.feature.staff.data.PermissionRequest;
import com.khanabook.saas.feature.staff.data.RoleTemplate;
import com.khanabook.saas.feature.platform.data.FeatureFlag;
import com.khanabook.saas.feature.onboarding.entity.MerchantAgreement;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.auth.entity.RefreshToken;
import com.khanabook.saas.feature.auth.entity.TokenBlocklist;
import com.khanabook.saas.feature.auth.entity.OtpRequest;
import com.khanabook.saas.feature.auth.entity.RateLimitAttempt;
import com.khanabook.saas.feature.auth.entity.SecurityAuditEvent;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.billing.data.BillItemRepository;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.menu.data.ItemRecipeRepository;
import com.khanabook.saas.feature.menu.data.MenuExtractionJobRepository;
import com.khanabook.saas.feature.inventory.data.RawMaterialRepository;
import com.khanabook.saas.feature.inventory.data.PurchaseOrderRepository;
import com.khanabook.saas.feature.inventory.data.StockMovementRepository;
import com.khanabook.saas.feature.inventory.data.StockLogRepository;
import com.khanabook.saas.feature.inventory.data.VendorRepository;
import com.khanabook.saas.feature.inventory.data.CustomerProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzPayoutRepository;
import com.khanabook.saas.feature.payments.data.ChargebackRepository;
import com.khanabook.saas.feature.payments.data.WebhookRetryJobRepository;
import com.khanabook.saas.feature.notifications.data.NotificationEventRepository;
import com.khanabook.saas.feature.notifications.data.DeviceTokenRepository;
import com.khanabook.saas.feature.compliance.data.FssaiTrackerRepository;
import com.khanabook.saas.feature.compliance.data.FssaiRenewalRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevisionRepository;
import com.khanabook.saas.feature.staff.data.PermissionRequestRepository;
import com.khanabook.saas.feature.staff.data.RoleTemplateRepository;
import com.khanabook.saas.feature.platform.data.FeatureFlagRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.repository.RefreshTokenRepository;
import com.khanabook.saas.feature.auth.repository.TokenBlocklistRepository;
import com.khanabook.saas.feature.auth.repository.OtpRequestRepository;
import com.khanabook.saas.feature.auth.repository.RateLimitAttemptRepository;
import com.khanabook.saas.feature.auth.repository.SecurityAuditLogRepository;
import com.khanabook.saas.feature.staff.service.PermissionService;
import com.khanabook.saas.core.utility.JwtUtility;
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
        User user = persistUser("user-e1-" + UUID.randomUUID(), RESTAURANT, UserRole.SHOP_STAFF);
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
        User user = persistUser("user-e1-multi-" + UUID.randomUUID(), RESTAURANT, UserRole.SHOP_STAFF);
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
        User user = persistUser("user-e1-monotonic-" + UUID.randomUUID(), RESTAURANT, UserRole.SHOP_STAFF);
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
        User user = persistUser("user-e1-redundant-" + UUID.randomUUID(), RESTAURANT, UserRole.SHOP_STAFF);
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
