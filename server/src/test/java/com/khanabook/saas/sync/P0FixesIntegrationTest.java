package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.utility.JwtUtility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 fixes integration tests:
 * P0-1: Clock skew rejection (>5min from server time → rejected)
 * P0-2: Payment/order status protection (paid → pending blocked by preserveServerOwnedState)
 * P0-3: Terminal sync status endpoint for daily closing accuracy
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class P0FixesIntegrationTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 9701L;

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository billRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private JwtUtility jwtUtility;
    @Autowired private ObjectMapper objectMapper;

    private String ownerToken;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-p0-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
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

    private String billJson(long localId, long updatedAt, String deviceId, String orderStatus, String paymentStatus) {
        return """
            [{
              "localId": %d,
              "deviceId": "%s",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              "dailyOrderId": %d,
              "dailyOrderDisplay": "%d",
              "lifetimeOrderId": %d,
              "orderType": "dine_in",
              "subtotal": 100.00,
              "totalAmount": 100.00,
              "paymentMode": "cash",
              "paymentStatus": "%s",
              "orderStatus": "%s"
            }]
            """.formatted(localId, deviceId, RESTAURANT, updatedAt, updatedAt,
                    localId, localId, localId, paymentStatus, orderStatus);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // P0-1: Clock skew rejection
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void p01_clockAheadRejected() throws Exception {
        // Terminal clock is 10 minutes ahead → push rejected
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        long futureTimestamp = System.currentTimeMillis() + (10 * 60 * 1000L); // +10 min

        var result = mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, futureTimestamp, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk())
                .andReturn();

        // Parse response — the record should be in failedLocalIds
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode failedIds = response.get("failedLocalIds");
        assertThat(failedIds).isNotNull();
        assertThat(failedIds.size()).isEqualTo(1);
        assertThat(failedIds.get(0).asLong()).isEqualTo(1L);

        // Verify failure reason mentions clock skew
        JsonNode reasons = response.get("failedReasons");
        assertThat(reasons).isNotNull();
        String reason = reasons.get("1").asText();
        assertThat(reason).contains("clock");
        assertThat(reason).contains("ahead");
    }

    @Test
    void p01_clockBehindRejected() throws Exception {
        // Terminal clock is 10 minutes behind → push rejected
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        long pastTimestamp = System.currentTimeMillis() - (10 * 60 * 1000L); // -10 min

        var result = mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, pastTimestamp, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode failedIds = response.get("failedLocalIds");
        assertThat(failedIds).isNotNull();
        assertThat(failedIds.size()).isEqualTo(1);

        JsonNode reasons = response.get("failedReasons");
        String reason = reasons.get("1").asText();
        assertThat(reason).contains("clock");
        assertThat(reason).contains("behind");
    }

    @Test
    void p01_clockWithinThreshold_accepted() throws Exception {
        // Terminal clock is 2 minutes ahead → within 5min threshold → accepted
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        long slightlyAhead = System.currentTimeMillis() + (2 * 60 * 1000L); // +2 min

        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, slightlyAhead, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
    }

    @Test
    void p01_exactThresholdEdge_accepted() throws Exception {
        // Exactly at the 5min boundary → accepted (skew > max is rejected, == is not)
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        long exactBoundary = System.currentTimeMillis() + (5 * 60 * 1000L); // exactly 5 min

        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, exactBoundary, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // P0-2: Payment/order status protection
    //
    // Timestamps must be within 5min of server time (P0-1 clock skew).
    // We use offsets relative to "now" so the P0-1 check passes, while
    // the second push always has a higher timestamp to trigger LWW.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void p02_paidBill_cannotRevertToPending() throws Exception {
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        long now = System.currentTimeMillis();

        // Push paid bill at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "completed", "paid")))
                .andExpect(status().isOk());

        // Push stale pending with higher timestamp at T0+1s
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now + 1000L, "DEV_A", "completed", "pending")))
                .andExpect(status().isOk());

        // Verify: paymentStatus is still "paid" — protection worked
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getPaymentStatus()).isEqualTo("paid");
    }

    @Test
    void p02_completedBill_cannotRevertToDraft() throws Exception {
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        long now = System.currentTimeMillis();

        // Push completed bill at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "completed", "pending")))
                .andExpect(status().isOk());

        // Push stale draft with higher timestamp at T0+1s
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now + 1000L, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        // Verify: orderStatus is still "completed"
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getOrderStatus()).isEqualTo("completed");
    }

    @Test
    void p02_cancelledBill_cannotBeUncancelled() throws Exception {
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        long now = System.currentTimeMillis();

        // Push cancelled bill at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "cancelled", "pending")))
                .andExpect(status().isOk());

        // Push stale draft with higher timestamp at T0+1s
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now + 1000L, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getOrderStatus()).isEqualTo("cancelled");
    }

    @Test
    void p02_draftBill_canStillProgressToCompleted() throws Exception {
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        long now = System.currentTimeMillis();

        // Push draft at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        // Push completed at T0+1s (forward transition — allowed)
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now + 1000L, "DEV_A", "completed", "paid")))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getOrderStatus()).isEqualTo("completed");
        assertThat(bills.get(0).getPaymentStatus()).isEqualTo("paid");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // P0-3: Terminal sync status endpoint
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void p03_terminalSyncStatus_returnsAllTerminals() throws Exception {
        createTerminal("A");
        createTerminal("B");

        var result = mockMvc.perform(get("/sync/terminal/sync-status")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.size()).isEqualTo(2);

        // Both should have syncState
        for (JsonNode terminal : response) {
            assertThat(terminal.has("terminalSeries")).isTrue();
            assertThat(terminal.has("syncState")).isTrue();
            assertThat(terminal.has("lastSyncAt")).isTrue();
            assertThat(terminal.has("secondsAgo")).isTrue();
            // syncState should be one of: live, recent, stale, offline, never
            String syncState = terminal.get("syncState").asText();
            assertThat(syncState).isIn("live", "recent", "stale", "offline", "never");
        }
    }

    @Test
    void p03_terminalSyncStatus_recentlySynced_showsLive() throws Exception {
        // Terminal was just updated → syncState should be "live"
        RestaurantTerminal t = createTerminal("A");

        var result = mockMvc.perform(get("/sync/terminal/sync-status")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.get(0).get("syncState").asText()).isEqualTo("live");
        assertThat(response.get(0).get("secondsAgo").asLong()).isLessThan(120);
    }

    @Test
    void p03_terminalSyncStatus_oldSync_showsStale() throws Exception {
        // Terminal was updated 30 minutes ago → syncState should be "stale"
        RestaurantTerminal t = createTerminal("A");
        t.setUpdatedAt(System.currentTimeMillis() - (30 * 60 * 1000L)); // 30 min ago
        terminalRepository.save(t);

        var result = mockMvc.perform(get("/sync/terminal/sync-status")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get(0).get("syncState").asText()).isEqualTo("stale");
    }
}
