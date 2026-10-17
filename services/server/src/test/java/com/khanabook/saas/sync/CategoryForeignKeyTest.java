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
import com.khanabook.saas.core.utility.JwtUtility;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Distributed state problem D2: Category FK dangling reference.
 *
 * Real use case: Category is soft-deleted via sync push. MenuItems still
 * reference it by serverCategoryId. Since soft-delete preserves the row,
 * the FK resolution still works. But if the category is re-created with
 * a new localId, the old MenuItems may resolve to the wrong category.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CategoryForeignKeyTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 9601L;

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private JwtUtility jwtUtility;
    @Autowired private ObjectMapper objectMapper;

    private String ownerToken;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-fk-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
        ownerToken = jwtUtility.generateToken(owner.getLoginId(), RESTAURANT, "OWNER");
    }

    private RestaurantTerminal createTerminal(String series) {
        RestaurantTerminal t = new RestaurantTerminal();
        t.setRestaurantId(RESTAURANT);
        t.setTerminalSeries(series);
        t.setTerminalName("Terminal " + series);
        t.setDeviceId("DEV_" + series);
        t.setIsActive(true);
        t.setCreatedAt(System.currentTimeMillis());
        t.setUpdatedAt(System.currentTimeMillis());
        return terminalRepository.save(t);
    }

    private String terminalToken(RestaurantTerminal t) {
        return jwtUtility.generateTerminalToken(
                "owner", RESTAURANT, "OWNER",
                t.getId().toString(), t.getTerminalSeries(), t.getDeviceId());
    }

    private Category createCategory(String name) {
        Category c = new Category();
        c.setRestaurantId(RESTAURANT);
        c.setName(name);
        c.setIsVeg(false);
        c.setSortOrder(1);
        c.setIsActive(true);
        c.setIsDeleted(false);
        c.setDeviceId("TEST");
        c.setLocalId(categoryRepository.count() + 1);
        c.setCreatedAt(System.currentTimeMillis());
        c.setUpdatedAt(System.currentTimeMillis());
        c.setServerUpdatedAt(System.currentTimeMillis());
        return categoryRepository.save(c);
    }

    private MenuItem createMenuItem(String name, Long categoryId) {
        MenuItem item = new MenuItem();
        item.setRestaurantId(RESTAURANT);
        item.setName(name);
        item.setCategoryId(categoryId);
        item.setServerCategoryId(categoryId);
        item.setBasePrice(new BigDecimal("250"));
        item.setIsAvailable(true);
        item.setIsDeleted(false);
        item.setDeviceId("TEST");
        item.setLocalId(menuItemRepository.count() + 1);
        item.setCreatedAt(System.currentTimeMillis());
        item.setUpdatedAt(System.currentTimeMillis());
        item.setServerUpdatedAt(System.currentTimeMillis());
        return menuItemRepository.save(item);
    }

    @Test
    void softDeletedCategory_stillResolvable() {
        // D2: Category is soft-deleted (isDeleted=true) but the row still exists.
        // MenuItem referencing it via serverCategoryId still resolves.
        Category category = createCategory("Starters");
        MenuItem item = createMenuItem("Samosa", category.getId());

        // Soft-delete the category
        category.setIsDeleted(true);
        categoryRepository.save(category);

        // Verify: category still exists in DB (soft delete)
        Category deleted = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(deleted.getIsDeleted()).isTrue();

        // MenuItem still references it
        MenuItem found = menuItemRepository.findById(item.getId()).orElseThrow();
        assertThat(found.getServerCategoryId()).isEqualTo(category.getId());
    }

    @Test
    void deletedCategory_menuItemStillHasValidCategoryId() {
        // D2: Even after category deletion, the MenuItem's serverCategoryId
        // still points to a valid row. No orphan at DB level.
        Category category = createCategory("Starters");
        MenuItem item = createMenuItem("Samosa", category.getId());

        // Delete category (soft)
        category.setIsDeleted(true);
        categoryRepository.save(category);

        // Verify: MenuItem's serverCategoryId is still the deleted category's ID
        MenuItem found = menuItemRepository.findById(item.getId()).orElseThrow();
        assertThat(found.getServerCategoryId()).isEqualTo(category.getId());

        // Verify: the referenced category row still exists
        assertThat(categoryRepository.findById(category.getId())).isPresent();
    }

    @Test
    void categoryLocalIdResolution_afterSoftDelete() {
        // D2: During sync, category localId is resolved to server ID via
        // buildMergedMap. Soft-deleted categories are still in the DB,
        // so the resolution succeeds.
        Category category = createCategory("Starters");

        // Simulate: category pushed with isDeleted=true via sync
        // The category row still exists, so resolveRelationalIds would
        // still find it in the bulk query
        category.setIsDeleted(true);
        categoryRepository.save(category);

        // Verify: category is findable by restaurantId (soft-deleted ones too)
        // The bulk query in buildMergedMap doesn't filter by isDeleted
        var allCategories = categoryRepository.findByRestaurantIdAndIsDeletedFalseAndIsActiveTrueOrderByNameAsc(
                RESTAURANT);
        // Soft-deleted category is excluded from active list
        assertThat(allCategories).isEmpty();

        // But the category row still exists — resolution via localId still works
        assertThat(categoryRepository.findById(category.getId())).isPresent();
    }

    @Test
    void reCreatedCategory_newLocalIdDifferentFromOld() {
        // D2: Owner deletes category "Starters" (localId=1) and creates a new
        // "Starters" (localId=2). Old MenuItems still reference localId=1 (deleted).
        // New MenuItems reference localId=2 (active).
        // No FK violation because there are no database-level FK constraints.
        Category oldCategory = createCategory("Starters");
        MenuItem oldItem = createMenuItem("Samosa", oldCategory.getId());

        // Soft-delete old category
        oldCategory.setIsDeleted(true);
        categoryRepository.save(oldCategory);

        // Create new category with same name
        Category newCategory = createCategory("Starters");
        MenuItem newItem = createMenuItem("Samosa New", newCategory.getId());

        // Verify: old item still references old (deleted) category
        MenuItem foundOld = menuItemRepository.findById(oldItem.getId()).orElseThrow();
        assertThat(foundOld.getServerCategoryId()).isEqualTo(oldCategory.getId());

        // Verify: new item references new category
        MenuItem foundNew = menuItemRepository.findById(newItem.getId()).orElseThrow();
        assertThat(foundNew.getServerCategoryId()).isEqualTo(newCategory.getId());

        // No FK violation — server_category_id is just a Long column
        assertThat(foundOld.getServerCategoryId()).isNotEqualTo(foundNew.getServerCategoryId());
    }
}
