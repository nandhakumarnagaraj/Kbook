package com.khanabook.saas.webadmin;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.storefront.entity.CustomerOrder;
import com.khanabook.saas.storefront.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WebAdminControllerTest extends BaseIntegrationTest {

    private static final Long RESTAURANT_ID = 9201L;
    private static final Long OTHER_RESTAURANT_ID = 9202L;

    @Autowired private MockMvc mockMvc;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ItemVariantRepository itemVariantRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private CustomerOrderRepository customerOrderRepository;

    @BeforeEach
    void seedData() {
        itemVariantRepository.deleteAll();
        menuItemRepository.deleteAll();
        categoryRepository.deleteAll();
        customerOrderRepository.deleteAll();
        billRepository.deleteAll();
        userRepository.deleteAll();
        restaurantProfileRepository.deleteAll();

        long now = System.currentTimeMillis();
        seedBusiness(RESTAURANT_ID, "Alpha Foods", now);
        seedBusiness(OTHER_RESTAURANT_ID, "Beta Foods", now + 1);

        persistUser("owner-alpha@test.com", RESTAURANT_ID, UserRole.OWNER);
        User ownerStaff = persistUser("owner-staff-alpha@test.com", RESTAURANT_ID, UserRole.OWNER);
        ownerStaff.setName("Alpha Owner Two");
        ownerStaff.setWhatsappNumber("9876500001");
        userRepository.save(ownerStaff);

        persistUser("owner-beta@test.com", OTHER_RESTAURANT_ID, UserRole.OWNER);

        Category category = new Category();
        category.setRestaurantId(RESTAURANT_ID);
        category.setLocalId(1L);
        category.setDeviceId("ADMIN_TEST");
        category.setName("Rice");
        category.setIsVeg(true);
        category.setIsActive(true);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        category.setServerUpdatedAt(now);
        Category savedCategory = categoryRepository.save(category);

        MenuItem menuItem = new MenuItem();
        menuItem.setRestaurantId(RESTAURANT_ID);
        menuItem.setLocalId(1L);
        menuItem.setDeviceId("ADMIN_TEST");
        menuItem.setCategoryId(savedCategory.getId());
        menuItem.setServerCategoryId(savedCategory.getId());
        menuItem.setName("Veg Fried Rice");
        menuItem.setDescription("Chef special");
        menuItem.setFoodType("veg");
        menuItem.setBasePrice(new BigDecimal("180.00"));
        menuItem.setIsAvailable(true);
        menuItem.setCurrentStock(new BigDecimal("15.0000"));
        menuItem.setCreatedAt(now);
        menuItem.setUpdatedAt(now);
        menuItem.setServerUpdatedAt(now);
        MenuItem savedMenuItem = menuItemRepository.save(menuItem);

        ItemVariant variant = new ItemVariant();
        variant.setRestaurantId(RESTAURANT_ID);
        variant.setLocalId(1L);
        variant.setDeviceId("ADMIN_TEST");
        variant.setMenuItemId(savedMenuItem.getId());
        variant.setServerMenuItemId(savedMenuItem.getId());
        variant.setVariantName("Full");
        variant.setPrice(new BigDecimal("180.00"));
        variant.setIsAvailable(true);
        variant.setCurrentStock(new BigDecimal("15.0000"));
        variant.setCreatedAt(now);
        variant.setUpdatedAt(now);
        variant.setServerUpdatedAt(now);
        itemVariantRepository.save(variant);

        com.khanabook.saas.entity.Bill bill = new com.khanabook.saas.entity.Bill();
        bill.setRestaurantId(RESTAURANT_ID);
        bill.setLocalId(1L);
        bill.setDeviceId("ADMIN_TEST");
        bill.setDailyOrderId(1L);
        bill.setDailyOrderDisplay("ORD-1");
        bill.setLifetimeOrderId(101L);
        bill.setOrderType("DINE_IN");
        bill.setCustomerName("Ravi");
        bill.setCustomerWhatsapp("9999999999");
        bill.setSubtotal(new BigDecimal("180.00"));
        bill.setTotalAmount(new BigDecimal("180.00"));
        bill.setPaymentMode("cash");
        bill.setPaymentStatus("paid");
        bill.setOrderStatus("completed");
        bill.setLastResetDate("2026-04-26");
        bill.setCreatedAt(now);
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
        billRepository.save(bill);

        com.khanabook.saas.entity.Bill pendingPosBill = new com.khanabook.saas.entity.Bill();
        pendingPosBill.setRestaurantId(RESTAURANT_ID);
        pendingPosBill.setLocalId(2L);
        pendingPosBill.setDeviceId("ADMIN_TEST");
        pendingPosBill.setDailyOrderId(2L);
        pendingPosBill.setDailyOrderDisplay("ORD-2");
        pendingPosBill.setLifetimeOrderId(102L);
        pendingPosBill.setOrderType("DINE_IN");
        pendingPosBill.setSubtotal(new BigDecimal("120.00"));
        pendingPosBill.setTotalAmount(new BigDecimal("120.00"));
        pendingPosBill.setPaymentMode("easebuzz");
        pendingPosBill.setPaymentStatus("pending");
        pendingPosBill.setOrderStatus("draft");
        pendingPosBill.setLastResetDate("2026-04-26");
        pendingPosBill.setCreatedAt(now + 2);
        pendingPosBill.setUpdatedAt(now + 2);
        pendingPosBill.setServerUpdatedAt(now + 2);
        billRepository.save(pendingPosBill);

        CustomerOrder customerOrder = new CustomerOrder();
        customerOrder.setRestaurantId(RESTAURANT_ID);
        customerOrder.setPublicOrderCode("KB9201-ORDER1");
        customerOrder.setTrackingToken("track-alpha");
        customerOrder.setCustomerName("Asha");
        customerOrder.setCustomerPhone("8888888888");
        customerOrder.setFulfillmentType("DELIVERY");
        customerOrder.setOrderStatus("PENDING_CONFIRMATION");
        customerOrder.setPaymentStatus("PENDING");
        customerOrder.setPaymentMethod("ONLINE");
        customerOrder.setSourceChannel("B2C_WEB");
        customerOrder.setCurrency("INR");
        customerOrder.setSubtotal(new BigDecimal("220.00"));
        customerOrder.setTotalAmount(new BigDecimal("220.00"));
        customerOrder.setCreatedAt(now + 5);
        customerOrder.setUpdatedAt(now + 5);
        customerOrderRepository.save(customerOrder);
    }

    @Test
    void kbookAdmin_can_access_platform_admin_apis() throws Exception {
        User admin = new User();
        admin.setName("Platform Admin");
        admin.setLoginId("platform@test.com");
        admin.setEmail("platform@test.com");
        admin.setAuthProvider(com.khanabook.saas.entity.AuthProvider.GOOGLE);
        admin.setPasswordHash(passwordEncoder.encode("pass123"));
        admin.setRestaurantId(0L);
        admin.setDeviceId("ADMIN_TEST");
        admin.setLocalId(999L);
        admin.setRole(UserRole.KBOOK_ADMIN);
        admin.setIsActive(true);
        admin.setCreatedAt(System.currentTimeMillis());
        admin.setUpdatedAt(System.currentTimeMillis());
        admin.setServerUpdatedAt(System.currentTimeMillis());
        userRepository.save(admin);
        String token = jwtUtility.generateToken("platform@test.com", null, UserRole.KBOOK_ADMIN.name());

        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBusinesses").value(2))
                .andExpect(jsonPath("$.totalOrders").value(3));

        mockMvc.perform(get("/admin/businesses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.restaurantId==9201)].shopName").value("Alpha Foods"));

        mockMvc.perform(get("/admin/businesses/{restaurantId}", RESTAURANT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("Alpha Foods"))
                .andExpect(jsonPath("$.onlineOrderCount").value(1));
    }

    @Test
    void owner_can_access_business_admin_apis() throws Exception {
        String ownerToken = persistUserAndGetToken("owner-web@test.com", RESTAURANT_ID, UserRole.OWNER);

        mockMvc.perform(get("/business/dashboard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("Alpha Foods"))
                .andExpect(jsonPath("$.onlineOrderCount").value(1))
                .andExpect(jsonPath("$.pendingPosPayments").value(1));

        mockMvc.perform(get("/business/orders")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceType").exists());

        mockMvc.perform(get("/business/menu")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Veg Fried Rice"));

        mockMvc.perform(get("/business/staff")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role=='OWNER')]").exists());
    }

    @Test
    void admin_cannot_access_business_admin_apis() throws Exception {
        String token = persistUserAndGetToken("admin-business@test.com", 0L, RESTAURANT_ID, UserRole.KBOOK_ADMIN);

        mockMvc.perform(get("/business/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private void seedBusiness(Long restaurantId, String shopName, long now) {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(restaurantId);
        profile.setLocalId(1L);
        profile.setDeviceId("ADMIN_TEST");
        profile.setShopName(shopName);
        profile.setCurrency("INR");
        profile.setOwnWebsiteEnabled(true);
        profile.setPrinterEnabled(true);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profile.setServerUpdatedAt(now);
        restaurantProfileRepository.save(profile);
    }
}
