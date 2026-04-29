package com.khanabook.saas.storefront;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicInvoiceControllerTest extends BaseIntegrationTest {

    private static final Long RESTAURANT_ID = 9001L;

    @Autowired private MockMvc mockMvc;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private BillItemRepository billItemRepository;

    private Long savedBillId;

    @BeforeEach
    void seed() {
        billItemRepository.deleteAll();
        billRepository.deleteAll();
        restaurantProfileRepository.deleteAll();

        long now = System.currentTimeMillis();

        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(RESTAURANT_ID);
        profile.setLocalId(1L);
        profile.setDeviceId("DEV_TEST");
        profile.setShopName("Anna Biriyani");
        profile.setShopAddress("Chennai");
        profile.setWhatsappNumber("9876543210");
        profile.setEmail("anna@example.com");
        profile.setFssaiNumber("12345678901234");
        profile.setCurrency("INR");
        profile.setGstEnabled(true);
        profile.setGstin("33ABCDE1234F1Z5");
        profile.setTimezone("Asia/Kolkata");
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profile.setServerUpdatedAt(now);
        restaurantProfileRepository.save(profile);

        Bill bill = new Bill();
        bill.setRestaurantId(RESTAURANT_ID);
        bill.setLocalId(101L);
        bill.setDeviceId("DEV_TEST");
        bill.setDailyOrderId(1L);
        bill.setDailyOrderDisplay("2026-04-28-7");
        bill.setLifetimeOrderId(1234L);
        bill.setOrderType("order");
        bill.setSubtotal(new BigDecimal("400.00"));
        bill.setCgstAmount(new BigDecimal("25.00"));
        bill.setSgstAmount(new BigDecimal("25.00"));
        bill.setTotalAmount(new BigDecimal("450.00"));
        bill.setPaymentMode("cash");
        bill.setPaymentStatus("paid");
        bill.setOrderStatus("completed");
        bill.setLastResetDate("2026-04-28");
        bill.setCreatedAt(now);
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
        Bill saved = billRepository.save(bill);
        savedBillId = saved.getId();

        BillItem item = new BillItem();
        item.setRestaurantId(RESTAURANT_ID);
        item.setLocalId(201L);
        item.setDeviceId("DEV_TEST");
        item.setBillId(101L);
        item.setServerBillId(savedBillId);
        item.setMenuItemId(1L);
        item.setItemName("Chicken Biryani");
        item.setQuantity(2);
        item.setPrice(new BigDecimal("200.00"));
        item.setItemTotal(new BigDecimal("400.00"));
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setServerUpdatedAt(now);
        billItemRepository.save(item);
    }

    @Test
    void rendersInvoiceHtml() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/public/invoice/{rid}/{bid}", RESTAURANT_ID, savedBillId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("Anna Biriyani");
        assertThat(html).contains("INV1234");
        assertThat(html).contains("Chicken Biryani");
        assertThat(html).contains("Rs. 450.00");
        assertThat(html).contains("<b>GSTIN:</b> 33ABCDE1234F1Z5");
        assertThat(html).contains("<b>FSSAI:</b> 12345678901234");
        assertThat(html).contains("<b>Email:</b> anna@example.com");
        assertThat(html).contains("<b>Contact Number:</b> 9876543210");
        assertThat(html).contains("Tax Invoice No");
        assertThat(html).contains("CGST");
        assertThat(html).contains("SGST");
        assertThat(html).contains("<b>Payment Type:</b> Cash");
        assertThat(html).contains("<th>Item</th><th class='c'>Qty</th><th class='r'>Total</th>");
    }

    @Test
    void returns404ForUnknownBill() throws Exception {
        mockMvc.perform(get("/public/invoice/{rid}/{bid}", RESTAURANT_ID, 99999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns404WhenRestaurantIdMismatches() throws Exception {
        mockMvc.perform(get("/public/invoice/{rid}/{bid}", 99999L, savedBillId))
                .andExpect(status().isNotFound());
    }
}
