package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.utility.JwtUtility;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Distributed state problem D4: Terminal-based access control gap.
 *
 * Real use case: Terminal A is configured as a KOT-only terminal (no billing).
 * When offline, Terminal A still pushes bills because the server doesn't
 * enforce terminal-type restrictions during sync.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TerminalAccessControlGapTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 8901L;

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository billRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private JwtUtility jwtUtility;
    @Autowired private ObjectMapper objectMapper;

    private String authToken() {
        String email = "owner-tac-" + UUID.randomUUID() + "@test.com";
        return persistUserAndGetToken(email, RESTAURANT, UserRole.OWNER);
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

    private String billJson(long localId, long updatedAt, String deviceId, String publicToken) {
        String tokenLine = publicToken == null ? "" : "\"publicToken\": \"" + publicToken + "\",";
        return """
            [{
              "localId": %d,
              "deviceId": "%s",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              %s
              "dailyOrderId": %d,
              "dailyOrderDisplay": "%d",
              "lifetimeOrderId": %d,
              "orderType": "dine_in",
              "subtotal": 100.00,
              "totalAmount": 100.00,
              "paymentMode": "cash",
              "paymentStatus": "pending",
              "orderStatus": "draft"
            }]
            """.formatted(localId, deviceId, RESTAURANT, updatedAt, updatedAt,
                    tokenLine, localId, localId, localId);
    }

    @Test
    void kotOnlyTerminal_stillPushesBills() throws Exception {
        RestaurantTerminal kotTerminal = createTerminal("KOT");
        String token = terminalToken(kotTerminal);
        String auth = authToken();

        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_KOT", null)))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
    }

    @Test
    void billingTerminal_canPushBills_normally() throws Exception {
        RestaurantTerminal billingTerminal = createTerminal("BILL");
        String token = terminalToken(billingTerminal);
        String auth = authToken();

        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_BILL", null)))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
    }

    @Test
    void disabledTerminal_billPushBehavior() throws Exception {
        RestaurantTerminal disabledTerminal = createTerminal("DIS");
        disabledTerminal.setIsActive(false);
        terminalRepository.save(disabledTerminal);

        String token = terminalToken(disabledTerminal);
        String auth = authToken();

        var result = mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_DIS", null)))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).isIn(200, 401, 403);
    }
}
