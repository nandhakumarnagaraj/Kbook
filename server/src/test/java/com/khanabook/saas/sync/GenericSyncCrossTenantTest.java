package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in the multi-tenant guard in GenericSyncService#handlePushSync: a bill whose
 * restaurantId differs from the JWT's tenant must be rejected with HTTP 403, never persisted.
 */
@AutoConfigureMockMvc
class GenericSyncCrossTenantTest extends BaseIntegrationTest {

    private static final Long RESTAURANT_A = 7301L;
    private static final Long RESTAURANT_B = 7302L;

    @Autowired private MockMvc mockMvc;

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
              "subtotal": 100.00,
              "totalAmount": 100.00,
              "paymentMode": "cash",
              "paymentStatus": "paid",
              "orderStatus": "completed"
            }]
            """.formatted(restaurantId);
    }

    @Test
    void push_billForAnotherRestaurant_isForbidden() throws Exception {
        // Token tenant = RESTAURANT_A, but the bill claims RESTAURANT_B.
        String tokenA = persistUserAndGetToken("owner-a@test.com", RESTAURANT_A, UserRole.OWNER);
        persistUser("owner-b@test.com", RESTAURANT_B, UserRole.OWNER);

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billJson(RESTAURANT_B)))
                .andExpect(status().isForbidden());
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
}
