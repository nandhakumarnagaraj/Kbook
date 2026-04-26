package com.khanabook.saas.storefront;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.storefront.dto.CreateCustomerOrderRequest;
import com.khanabook.saas.storefront.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicStorefrontControllerTest extends BaseIntegrationTest {

    private static final Long RESTAURANT_ID = 7001L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private ItemVariantRepository itemVariantRepository;
    @Autowired private CustomerOrderRepository customerOrderRepository;

    @BeforeEach
    void seedStorefront() {
        itemVariantRepository.deleteAll();
        menuItemRepository.deleteAll();
        categoryRepository.deleteAll();
        customerOrderRepository.deleteAll();
        restaurantProfileRepository.deleteAll();

        long now = System.currentTimeMillis();

        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(RESTAURANT_ID);
        profile.setLocalId(1L);
        profile.setDeviceId("PUBLIC_WEB");
        profile.setShopName("Public Demo Store");
        profile.setCurrency("INR");
        profile.setOwnWebsiteEnabled(true);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profile.setServerUpdatedAt(now);
        restaurantProfileRepository.save(profile);

        Category category = new Category();
        category.setRestaurantId(RESTAURANT_ID);
        category.setLocalId(1L);
        category.setDeviceId("PUBLIC_WEB");
        category.setName("Biryani");
        category.setIsVeg(false);
        category.setIsActive(true);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        category.setServerUpdatedAt(now);
        Category savedCategory = categoryRepository.save(category);

        MenuItem menuItem = new MenuItem();
        menuItem.setRestaurantId(RESTAURANT_ID);
        menuItem.setLocalId(1L);
        menuItem.setDeviceId("PUBLIC_WEB");
        menuItem.setCategoryId(savedCategory.getId());
        menuItem.setServerCategoryId(savedCategory.getId());
        menuItem.setName("Chicken Biryani");
        menuItem.setDescription("House special");
        menuItem.setBasePrice(new BigDecimal("180.00"));
        menuItem.setFoodType("non_veg");
        menuItem.setIsAvailable(true);
        menuItem.setCurrentStock(new BigDecimal("25.0000"));
        menuItem.setCreatedAt(now);
        menuItem.setUpdatedAt(now);
        menuItem.setServerUpdatedAt(now);
        MenuItem savedMenuItem = menuItemRepository.save(menuItem);

        ItemVariant variant = new ItemVariant();
        variant.setRestaurantId(RESTAURANT_ID);
        variant.setLocalId(1L);
        variant.setDeviceId("PUBLIC_WEB");
        variant.setMenuItemId(savedMenuItem.getId());
        variant.setServerMenuItemId(savedMenuItem.getId());
        variant.setVariantName("Half");
        variant.setPrice(new BigDecimal("110.00"));
        variant.setIsAvailable(true);
        variant.setCurrentStock(new BigDecimal("25.0000"));
        variant.setCreatedAt(now);
        variant.setUpdatedAt(now);
        variant.setServerUpdatedAt(now);
        itemVariantRepository.save(variant);
    }

    @Test
    void publicCatalog_returnsPublishedItems() throws Exception {
        mockMvc.perform(get("/public/stores/{restaurantId}/catalog", RESTAURANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(RESTAURANT_ID))
                .andExpect(jsonPath("$.shopName").value("Public Demo Store"))
                .andExpect(jsonPath("$.categories[0].name").value("Biryani"))
                .andExpect(jsonPath("$.categories[0].items[0].name").value("Chicken Biryani"));
    }

    @Test
    void createOrder_and_trackOrder_workEndToEnd() throws Exception {
        MenuItem menuItem = menuItemRepository.findAll().get(0);
        ItemVariant variant = itemVariantRepository.findAll().get(0);

        CreateCustomerOrderRequest request = new CreateCustomerOrderRequest();
        request.setCustomerName("Asha");
        request.setCustomerPhone("9876543210");
        request.setFulfillmentType("pickup");
        request.setPaymentMethod("upi");

        CreateCustomerOrderRequest.LineItem lineItem = new CreateCustomerOrderRequest.LineItem();
        lineItem.setMenuItemId(menuItem.getId());
        lineItem.setVariantId(variant.getId());
        lineItem.setQuantity(2);
        lineItem.setSpecialInstruction("Less spicy");
        request.setItems(List.of(lineItem));

        String response = mockMvc.perform(post("/public/stores/{restaurantId}/orders", RESTAURANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.orderStatus").value("PENDING_CONFIRMATION"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String trackingToken = objectMapper.readTree(response).get("trackingToken").asText();
        assertThat(customerOrderRepository.findByTrackingToken(trackingToken)).isPresent();

        mockMvc.perform(get("/public/orders/{trackingToken}", trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Asha"))
                .andExpect(jsonPath("$.items[0].itemName").value("Chicken Biryani"))
                .andExpect(jsonPath("$.items[0].variantName").value("Half"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }
}
