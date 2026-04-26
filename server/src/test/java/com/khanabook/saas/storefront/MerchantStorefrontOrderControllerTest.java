package com.khanabook.saas.storefront;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.storefront.entity.CustomerOrder;
import com.khanabook.saas.storefront.entity.CustomerOrderItem;
import com.khanabook.saas.storefront.repository.CustomerOrderItemRepository;
import com.khanabook.saas.storefront.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MerchantStorefrontOrderControllerTest extends BaseIntegrationTest {

    private static final Long RESTAURANT_ID = 8101L;
    private static final Long OTHER_RESTAURANT_ID = 8102L;

    @Autowired private MockMvc mockMvc;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private ItemVariantRepository itemVariantRepository;
    @Autowired private CustomerOrderRepository customerOrderRepository;
    @Autowired private CustomerOrderItemRepository customerOrderItemRepository;

    private Long seededOrderId;

    @BeforeEach
    void seedOrders() {
        customerOrderItemRepository.deleteAll();
        customerOrderRepository.deleteAll();
        itemVariantRepository.deleteAll();
        menuItemRepository.deleteAll();
        categoryRepository.deleteAll();
        restaurantProfileRepository.deleteAll();

        long now = System.currentTimeMillis();
        seedRestaurant(RESTAURANT_ID, "Merchant Demo Store", now);
        seedRestaurant(OTHER_RESTAURANT_ID, "Other Store", now);

        Category category = new Category();
        category.setRestaurantId(RESTAURANT_ID);
        category.setLocalId(1L);
        category.setDeviceId("STORE_WEB");
        category.setName("Meals");
        category.setIsVeg(false);
        category.setIsActive(true);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        category.setServerUpdatedAt(now);
        Category savedCategory = categoryRepository.save(category);

        MenuItem menuItem = new MenuItem();
        menuItem.setRestaurantId(RESTAURANT_ID);
        menuItem.setLocalId(1L);
        menuItem.setDeviceId("STORE_WEB");
        menuItem.setCategoryId(savedCategory.getId());
        menuItem.setServerCategoryId(savedCategory.getId());
        menuItem.setName("Paneer Rice Bowl");
        menuItem.setDescription("Dinner combo");
        menuItem.setBasePrice(new BigDecimal("220.00"));
        menuItem.setFoodType("veg");
        menuItem.setIsAvailable(true);
        menuItem.setCurrentStock(new BigDecimal("20.0000"));
        menuItem.setCreatedAt(now);
        menuItem.setUpdatedAt(now);
        menuItem.setServerUpdatedAt(now);
        MenuItem savedMenuItem = menuItemRepository.save(menuItem);

        ItemVariant variant = new ItemVariant();
        variant.setRestaurantId(RESTAURANT_ID);
        variant.setLocalId(1L);
        variant.setDeviceId("STORE_WEB");
        variant.setMenuItemId(savedMenuItem.getId());
        variant.setServerMenuItemId(savedMenuItem.getId());
        variant.setVariantName("Regular");
        variant.setPrice(new BigDecimal("220.00"));
        variant.setIsAvailable(true);
        variant.setCurrentStock(new BigDecimal("20.0000"));
        variant.setCreatedAt(now);
        variant.setUpdatedAt(now);
        variant.setServerUpdatedAt(now);
        ItemVariant savedVariant = itemVariantRepository.save(variant);

        CustomerOrder order = new CustomerOrder();
        order.setRestaurantId(RESTAURANT_ID);
        order.setPublicOrderCode("KB8101-ORDER1");
        order.setTrackingToken("track8101");
        order.setCustomerName("Nila");
        order.setCustomerPhone("9999999999");
        order.setCustomerNote("Gate delivery");
        order.setFulfillmentType("DELIVERY");
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setPaymentStatus("PENDING");
        order.setPaymentMethod("ONLINE");
        order.setSourceChannel("B2C_WEB");
        order.setCurrency("INR");
        order.setSubtotal(new BigDecimal("220.00"));
        order.setTotalAmount(new BigDecimal("220.00"));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        CustomerOrder savedOrder = customerOrderRepository.save(order);
        seededOrderId = savedOrder.getId();

        CustomerOrderItem orderItem = new CustomerOrderItem();
        orderItem.setCustomerOrderId(savedOrder.getId());
        orderItem.setMenuItemId(savedMenuItem.getId());
        orderItem.setItemVariantId(savedVariant.getId());
        orderItem.setItemName(savedMenuItem.getName());
        orderItem.setVariantName(savedVariant.getVariantName());
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(savedVariant.getPrice());
        orderItem.setLineTotal(savedVariant.getPrice());
        orderItem.setSpecialInstruction("Extra spoon");
        orderItem.setCreatedAt(now);
        customerOrderItemRepository.save(orderItem);

        CustomerOrder otherOrder = new CustomerOrder();
        otherOrder.setRestaurantId(OTHER_RESTAURANT_ID);
        otherOrder.setPublicOrderCode("KB8102-ORDER1");
        otherOrder.setTrackingToken("track8102");
        otherOrder.setCustomerName("Other");
        otherOrder.setFulfillmentType("PICKUP");
        otherOrder.setOrderStatus("PENDING_CONFIRMATION");
        otherOrder.setPaymentStatus("PENDING");
        otherOrder.setPaymentMethod("COD");
        otherOrder.setSourceChannel("B2C_WEB");
        otherOrder.setCurrency("INR");
        otherOrder.setSubtotal(new BigDecimal("100.00"));
        otherOrder.setTotalAmount(new BigDecimal("100.00"));
        otherOrder.setCreatedAt(now + 1);
        otherOrder.setUpdatedAt(now + 1);
        customerOrderRepository.save(otherOrder);
    }

    @Test
    void owner_can_list_only_their_orders() throws Exception {
        String token = persistUserAndGetToken("owner-store@test.com", RESTAURANT_ID, UserRole.OWNER);

        mockMvc.perform(get("/storefront/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].publicOrderCode").value("KB8101-ORDER1"))
                .andExpect(jsonPath("$[0].customerName").value("Nila"));
    }

    @Test
    void owner_can_get_and_update_their_order() throws Exception {
        String token = persistUserAndGetToken("owner-store-2@test.com", RESTAURANT_ID, UserRole.OWNER);

        mockMvc.perform(get("/storefront/orders/{orderId}", seededOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingToken").value("track8101"))
                .andExpect(jsonPath("$.items[0].itemName").value("Paneer Rice Bowl"));

        mockMvc.perform(patch("/storefront/orders/{orderId}/status", seededOrderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderStatus": "accepted",
                                  "customerNote": "Accepted for dispatch"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.customerNote").value("Accepted for dispatch"));

        CustomerOrder updated = customerOrderRepository.findById(seededOrderId).orElseThrow();
        assertThat(updated.getOrderStatus()).isEqualTo("ACCEPTED");
        assertThat(updated.getCustomerNote()).isEqualTo("Accepted for dispatch");
    }

    @Test
    void owner_cannot_access_other_restaurant_order() throws Exception {
        String token = persistUserAndGetToken("owner-store-3@test.com", RESTAURANT_ID, UserRole.OWNER);

        Long otherOrderId = customerOrderRepository.findByTrackingToken("track8102").orElseThrow().getId();

        mockMvc.perform(get("/storefront/orders/{orderId}", otherOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Order not found"));
    }

    @Test
    void invalid_transition_is_rejected() throws Exception {
        String token = persistUserAndGetToken("owner-store-4@test.com", RESTAURANT_ID, UserRole.OWNER);

        mockMvc.perform(patch("/storefront/orders/{orderId}/status", seededOrderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderStatus": "completed"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported order status transition: PENDING_CONFIRMATION -> COMPLETED"));
    }

    @Test
    void unauthenticated_user_cannot_access_merchant_order_api() throws Exception {
        mockMvc.perform(get("/storefront/orders"))
                .andExpect(status().isUnauthorized());
    }

    private void seedRestaurant(Long restaurantId, String shopName, long now) {
        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(restaurantId);
        profile.setLocalId(1L);
        profile.setDeviceId("STORE_WEB");
        profile.setShopName(shopName);
        profile.setCurrency("INR");
        profile.setOwnWebsiteEnabled(true);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profile.setServerUpdatedAt(now);
        restaurantProfileRepository.save(profile);
    }
}
