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
 * Distributed state problem A1/A2/A3: LWW conflict resolution — integration tests.
 *
 * Hits the real /sync/bills/push endpoint and verifies which record wins
 * when two pushes have different timestamps (clock skew scenario).
 *
 * NOTE: Timestamps must be within 5min of server time (P0-1 clock skew check).
 * P0-2 protects paid/completed/cancelled from LWW reversion.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LwwConflictResolutionIntegrationTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 9201L;

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository billRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private JwtUtility jwtUtility;
    @Autowired private ObjectMapper objectMapper;

    private String authToken() {
        return persistUserAndGetToken("owner-lww-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
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

    // ── A1: Clock skew — newer timestamp wins via real endpoint ────────

    @Test
    void lww_newerTimestamp_overwritesOlder() throws Exception {
        // Same device pushes same bill twice: first "draft" at T0, then
        // "completed" at T0+1s. LWW should accept the newer one.
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        String auth = authToken();
        long now = System.currentTimeMillis();

        // First push: draft at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        // Second push: completed at T0+1s (newer, same device + localId)
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now + 1000L, "DEV_A", "completed", "pending")))
                .andExpect(status().isOk());

        // Verify: bill should be "completed" — LWW accepted the newer push
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getOrderStatus()).isEqualTo("completed");
        assertThat(bills.get(0).getUpdatedAt()).isEqualTo(now + 1000L);
    }

    @Test
    void lww_olderTimestamp_rejectedByServer() throws Exception {
        // Same device pushes "completed" at T0, then "draft" at T0-1s (stale).
        // LWW should reject the stale push.
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        String auth = authToken();
        long now = System.currentTimeMillis();

        // First push: completed at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "completed", "pending")))
                .andExpect(status().isOk());

        // Second push: draft at T0-1s (stale)
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now - 1000L, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        // Verify: bill is still "completed" — stale push was rejected
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getOrderStatus()).isEqualTo("completed");
        assertThat(bills.get(0).getUpdatedAt()).isEqualTo(now);
    }

    // ── A2: Bill state machine — P0-2 protects finalized bills ───────
    // After P0-2, paid/completed/cannot be reverted by LWW.

    @Test
    void lww_cannotRevertPaidBill_evenWithHigherTimestamp() throws Exception {
        // Bill is "paid" at T0. Device with clock ahead pushes "draft" at T0+1s.
        // P0-2 protection: paymentStatus="paid" is preserved despite LWW.
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        String auth = authToken();
        long now = System.currentTimeMillis();

        // Push bill as "paid" at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "completed", "paid")))
                .andExpect(status().isOk());

        // Push same bill as "draft" at T0+1s (higher timestamp)
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now + 1000L, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        // Verify: P0-2 protected the paid status — bill is still completed/paid
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getPaymentStatus()).isEqualTo("paid");
        assertThat(bills.get(0).getOrderStatus()).isEqualTo("completed");
    }

    @Test
    void lww_paymentStatus_paidProtectedFromRevert() throws Exception {
        // Gateway marks bill "paid" at T0. Device pushes "pending" at T0+1s.
        // P0-2: paymentStatus="paid" is terminal — cannot be reverted.
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        String auth = authToken();
        long now = System.currentTimeMillis();

        // Push bill as "paid" at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "completed", "paid")))
                .andExpect(status().isOk());

        // Push same bill as "pending" at T0+1s (stale device)
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now + 1000L, "DEV_A", "completed", "pending")))
                .andExpect(status().isOk());

        // Verify: payment status is still "paid" — protected
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getPaymentStatus()).isEqualTo("paid");
    }

    // ── A3: Equal timestamps — second push wins (>= favors incoming) ──

    @Test
    void lww_equalTimestamp_completedProtectedFromDowngrade() throws Exception {
        // Two pushes at the exact same millisecond from the same device.
        // The >= operator means equal timestamps favor the incoming record.
        // But P0-2 protects completed/paid from reversion, so the second
        // push with "draft" is accepted but completed status is preserved.
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        String auth = authToken();
        long now = System.currentTimeMillis();

        // Push "completed" first at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "completed", "pending")))
                .andExpect(status().isOk());

        // Push "draft" at same timestamp T0 (>= favors incoming for non-protected fields)
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        // Verify: LWW accepted second push, but P0-2 protected completed status
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getOrderStatus()).isEqualTo("completed");
    }

    @Test
    void lww_equalTimestamp_nonProtectedFieldReverted() throws Exception {
        // Two pushes at the exact same millisecond, same device.
        // For non-protected fields (e.g. totalAmount), LWW favors incoming.
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);
        String auth = authToken();
        long now = System.currentTimeMillis();

        // Push draft with totalAmount=100 at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, now, "DEV_A", "draft", "pending")))
                .andExpect(status().isOk());

        // Push draft with totalAmount=200 at same T0 — LWW >= favors incoming
        String updatedBill = """
            [{
              "localId": 1,
              "deviceId": "DEV_A",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              "dailyOrderId": 1,
              "dailyOrderDisplay": "1",
              "lifetimeOrderId": 1,
              "orderType": "dine_in",
              "subtotal": 200.00,
              "totalAmount": 200.00,
              "paymentMode": "cash",
              "paymentStatus": "pending",
              "orderStatus": "draft"
            }]
            """.formatted(RESTAURANT, now, now);
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", token)
                .content(updatedBill))
                .andExpect(status().isOk());

        // Verify: non-protected field updated via LWW
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getTotalAmount().doubleValue()).isEqualTo(200.00d);
    }

    // ── Multiple terminals create separate bills (not LWW) ────────────

    @Test
    void differentDevices_createSeparateBills() throws Exception {
        // Two different terminals push bills with the same localId=1 but
        // different deviceIds. These are DIFFERENT bills (each device has
        // its own localId namespace). This is correct behavior, not a bug.
        RestaurantTerminal tA = createTerminal("A");
        RestaurantTerminal tB = createTerminal("B");
        String tokenA = terminalToken(tA);
        String tokenB = terminalToken(tB);
        String auth = authToken();
        long now = System.currentTimeMillis();

        // Terminal A pushes at T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, now, "DEV_A", "completed", "pending")))
                .andExpect(status().isOk());

        // Terminal B pushes at T0+1s with same localId=1 but different deviceId
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenB)
                .content(billJson(1L, now + 1000L, "DEV_B", "draft", "pending")))
                .andExpect(status().isOk());

        // Verify: two separate bills — each device owns its localId space
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(2);
    }
}
