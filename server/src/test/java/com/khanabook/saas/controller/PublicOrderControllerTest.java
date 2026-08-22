package com.khanabook.saas.controller;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 QR ordering core: public menu exposure is customer-safe, order totals
 * are computed server-side, unavailable items/variants are rejected, and the
 * created bill lands as a draft owned by the QR_ORDER pseudo-device.
 */
@AutoConfigureMockMvc
class PublicOrderControllerTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private BillRepository billRepository;

    // Unique restaurant per test: the integration H2 database is shared across
    // test methods/classes, so isolation comes from fresh tenant IDs.
    private Long restaurant;

    @org.junit.jupiter.api.BeforeEach
    void initRestaurant() {
        restaurant = 8_800_000L + (long) (Math.random() * 100_000);
    }

    private long now() { return System.currentTimeMillis(); }

    private long seq = 1;

    private void fillSync(com.khanabook.saas.sync.entity.BaseSyncEntity e, Long restaurantId) {
        e.setLocalId(seq++);
        e.setDeviceId("SEED");
        e.setRestaurantId(restaurantId);
        long t = now();
        e.setCreatedAt(t);
        e.setUpdatedAt(t);
        e.setServerUpdatedAt(t);
    }

    private Long seedCategory() {
        Category c = new Category();
        fillSync(c, restaurant);
        c.setName("Starters");
        c.setIsVeg(true);
        c.setIsActive(true);
        c.setSortOrder(1);
        return categoryRepository.save(c).getId();
    }

    private MenuItem seedItem(String name, boolean available, double price) {
        MenuItem m = new MenuItem();
        fillSync(m, restaurant);
        m.setCategoryId(seedCategory());
        m.setName(name);
        m.setBasePrice(java.math.BigDecimal.valueOf(price));
        m.setIsAvailable(available);
        return menuItemRepository.save(m);
    }

    private String body(long itemId, int qty) {
        return """
            {"items":[{"menuItemId":%d,"quantity":%d}],"orderType":"dine_in","tableLabel":"T4"}
            """.formatted(itemId, qty);
    }

    @Test
    void menu_exposesOnlyAvailableItems_withCustomerSafeFields() throws Exception {
        seedItem("Paneer Tikka", true, 180.00);
        seedItem("Hidden Dish", false, 999.00);

        mockMvc.perform(get("/public/restaurants/" + restaurant + "/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Paneer Tikka"))
                .andExpect(jsonPath("$.items[0].basePrice").isNotEmpty());
    }

    @Test
    void order_totalComputedFromServerPrices_andBillLandsAsDraft() throws Exception {
        MenuItem item = seedItem("Dosa", true, 90.00);

        String body = mockMvc.perform(post("/public/restaurants/" + restaurant + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(item.getId(), 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(270.0))
                .andReturn().getResponse().getContentAsString();

        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        long orderId = root.get("orderId").asLong();
        Bill bill = billRepository.findById(orderId).orElseThrow();
        assertThat(bill.getOrderStatus()).isEqualTo("draft");
        assertThat(bill.getPaymentStatus()).isEqualTo("pending");
        assertThat(bill.getSourceChannel()).isEqualTo("own_website");
        assertThat(bill.getTotalAmount()).isEqualByComparingTo("270.00");
    }

    @Test
    void order_rejectsUnavailableItem() throws Exception {
        MenuItem hidden = seedItem("Sold Out", false, 50.00);
        mockMvc.perform(post("/public/restaurants/" + restaurant + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(hidden.getId(), 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void order_rejectsInvalidQuantity() throws Exception {
        MenuItem item = seedItem("Chai", true, 20.00);
        mockMvc.perform(post("/public/restaurants/" + restaurant + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(item.getId(), 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void order_rejectsOtherTenantsItem() throws Exception {
        Long otherRestaurant = restaurant + 1;
        Category c = new Category();
        fillSync(c, otherRestaurant);
        c.setName("Other");
        c.setIsVeg(true);
        c.setIsActive(true);
        MenuItem foreign = new MenuItem();
        fillSync(foreign, otherRestaurant);
        foreign.setCategoryId(categoryRepository.save(c).getId());
        foreign.setName("Foreign Item");
        foreign.setBasePrice(java.math.BigDecimal.TEN);
        foreign.setIsAvailable(true);
        MenuItem saved = menuItemRepository.save(foreign);

        mockMvc.perform(post("/public/restaurants/" + restaurant + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(saved.getId(), 1)))
                .andExpect(status().isBadRequest());
    }
}
