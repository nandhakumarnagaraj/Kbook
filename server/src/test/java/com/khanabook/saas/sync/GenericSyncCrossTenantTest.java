package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the multi-tenant guard in GenericSyncService#handlePushSync: a bill whose
 * restaurantId differs from the JWT's tenant is auto-corrected and accepted (JWT already
 * enforces tenant scope).
 */
@AutoConfigureMockMvc
class GenericSyncCrossTenantTest extends BaseIntegrationTest {

    private static final Long RESTAURANT_A = 7301L;
    private static final Long RESTAURANT_B = 7302L;

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository billRepository;

    private String billJson(Long restaurantId) {
        return """
            [{
              "localId": 1,
              "deviceId": "DEV_A",
              "restaurantId": %d,
              "updatedAt": 1000,
              "createdAt": 1000,
              "isDeleted": false,
              "dailyOrderId": 1,
              "dailyOrderDisplay": "1",
              "lifetimeOrderId": 1,
              "orderType": "dine-in",
              "subtotal": 100.00,
              "totalAmount": 100.00,
              "paymentMode": "cash",
              "paymentStatus": "paid",
              "orderStatus": "completed"
            }]
            """.formatted(restaurantId);
    }

    @Test
    void push_billForAnotherRestaurant_isAutoCorrected() throws Exception {
        // Token tenant = RESTAURANT_A, but the bill claims RESTAURANT_B.
        String tokenA = persistUserAndGetToken("owner-a@test.com", RESTAURANT_A, UserRole.OWNER);
        persistUser("owner-b@test.com", RESTAURANT_B, UserRole.OWNER);

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billJson(RESTAURANT_B)))
                .andExpect(status().isOk());

        var bill = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT_A, "DEV_A", 1L);
        assertThat(bill).isPresent();
    }

    @Test
    void push_billForOwnRestaurant_isAccepted() throws Exception {
        String tokenA = persistUserAndGetToken("owner-a2@test.com", RESTAURANT_A, UserRole.OWNER);

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billJson(RESTAURANT_A)))
                .andExpect(status().isOk());
    }

    @Test
    void push_sameDeviceAndLocalIdForDifferentRestaurants_createsSeparateBills() throws Exception {
        String tokenA = persistUserAndGetToken("owner-a3@test.com", RESTAURANT_A, UserRole.OWNER);
        String tokenB = persistUserAndGetToken("owner-b3@test.com", RESTAURANT_B, UserRole.OWNER);

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billJson(RESTAURANT_A)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billJson(RESTAURANT_B)))
                .andExpect(status().isOk());

        var billA = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT_A, "DEV_A", 1L);
        var billB = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT_B, "DEV_A", 1L);

        assertThat(billA).isPresent();
        assertThat(billB).isPresent();
        assertThat(billA.get().getId()).isNotEqualTo(billB.get().getId());
    }
}
