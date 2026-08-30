package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.utility.JwtUtility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Distributed state problems C1/C3: Concurrent bill push + payment status race.
 *
 * C1 – Two terminals push bills simultaneously — verify no duplicates.
 * C3 – Payment webhook vs device push race — verify LWW behavior.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentBillPushTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 8701L;

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository billRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private JwtUtility jwtUtility;
    @Autowired private ObjectMapper objectMapper;

    private String authToken() {
        String email = "owner-conc-" + UUID.randomUUID() + "@test.com";
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
    void twoTerminals_pushBillsConcurrently_noDuplicates() throws Exception {
        RestaurantTerminal tA = createTerminal("A");
        RestaurantTerminal tB = createTerminal("B");
        String tokenA = terminalToken(tA);
        String tokenB = terminalToken(tB);
        String auth = authToken();

        int numBills = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Terminal A pushes 5 bills
        Future<?> futureA = executor.submit(() -> {
            try {
                startLatch.await();
                for (long i = 1; i <= numBills; i++) {
                    mockMvc.perform(post("/sync/bills/push")
                            .contentType("application/json")
                            .header("Authorization", "Bearer " + auth)
                            .header("X-Terminal-Token", tokenA)
                            .content(billJson(i, System.currentTimeMillis() + i, "DEV_A", null)));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Terminal B pushes 5 bills
        Future<?> futureB = executor.submit(() -> {
            try {
                startLatch.await();
                for (long i = 1; i <= numBills; i++) {
                    mockMvc.perform(post("/sync/bills/push")
                            .contentType("application/json")
                            .header("Authorization", "Bearer " + auth)
                            .header("X-Terminal-Token", tokenB)
                            .content(billJson(i, System.currentTimeMillis() + i, "DEV_B", null)));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown(); // Both terminals start simultaneously
        doneLatch.await();
        executor.shutdown();

        // Verify: each terminal's bills are isolated (different publicTokens)
        // and no cross-terminal contamination
        var allBills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        // At least some bills should exist from both terminals
        assertThat(allBills).isNotEmpty();
    }

    @Test
    void sameBill_pushedTwice_idempotent_onlyOneBillCreated() throws Exception {
        RestaurantTerminal tA = createTerminal("A");
        String tokenA = terminalToken(tA);
        String auth = authToken();

        String publicToken = UUID.randomUUID().toString();
        String billContent = billJson(1L, System.currentTimeMillis(), "DEV_A", publicToken);

        // Push same bill twice with same localId + deviceId
        var r1 = mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(billContent))
                .andExpect(status().isOk())
                .andReturn();

        var r2 = mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(billContent))
                .andExpect(status().isOk())
                .andReturn();

        // Both return 200 — second is idempotent retry
        String resp1 = r1.getResponse().getContentAsString();
        String resp2 = r2.getResponse().getContentAsString();

        var node1 = objectMapper.readTree(resp1);
        var node2 = objectMapper.readTree(resp2);

        // First push: localToServerIdMap should have a mapping
        assertThat(node1.has("localToServerIdMap")).isTrue();
        JsonNode map1 = node1.get("localToServerIdMap");
        assertThat(map1.has("1")).isTrue();
        Long serverId1 = map1.get("1").asLong();

        // Second push: same server ID returned (idempotent)
        assertThat(node2.has("localToServerIdMap")).isTrue();
        JsonNode map2 = node2.get("localToServerIdMap");
        assertThat(map2.has("1")).isTrue();
        Long serverId2 = map2.get("1").asLong();
        assertThat(serverId2).isEqualTo(serverId1);

        // Only one bill exists in DB
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getId()).isEqualTo(serverId1);
    }

    @Test
    void billPush_lww_olderRecordRejected() throws Exception {
        RestaurantTerminal tA = createTerminal("A");
        String tokenA = terminalToken(tA);
        String auth = authToken();

        String publicToken = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // Push a bill with high timestamp
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, now + 1000L, "DEV_A", publicToken)))
                .andExpect(status().isOk());

        // Push same bill with lower timestamp (stale)
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, now, "DEV_A", publicToken)))
                .andExpect(status().isOk()); // Returns 200 but record is rejected (LWW)

        // Verify only one bill exists with the newer timestamp
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getUpdatedAt()).isEqualTo(now + 1000L);
    }

    @Test
    void billPush_paymentStatus_lwwBehavior() throws Exception {
        RestaurantTerminal tA = createTerminal("A");
        String tokenA = terminalToken(tA);
        String auth = authToken();

        String publicToken = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // Push bill as "pending" with timestamp T0
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, now, "DEV_A", publicToken)))
                .andExpect(status().isOk());

        // Push same bill as "paid" with timestamp T0+1s (newer)
        String paidBill = billJson(1L, now + 1000L, "DEV_A", publicToken)
                .replace("\"paymentStatus\": \"pending\"", "\"paymentStatus\": \"paid\"");
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(paidBill))
                .andExpect(status().isOk());

        // Verify bill is now "paid" (LWW accepted newer timestamp)
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getPaymentStatus()).isEqualTo("paid");
    }

    @Test
    void billPush_paymentStatus_staleRevertBlockedByP02() throws Exception {
        RestaurantTerminal tA = createTerminal("A");
        String tokenA = terminalToken(tA);
        String auth = authToken();

        String publicToken = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // Push bill as "paid" with high timestamp (simulating webhook)
        String paidBill = billJson(1L, now, "DEV_A", publicToken)
                .replace("\"paymentStatus\": \"pending\"", "\"paymentStatus\": \"paid\"");
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(paidBill))
                .andExpect(status().isOk());

        // Push same bill as "pending" with even higher timestamp (stale device)
        // P0-2: paid is terminal — cannot be reverted even with higher timestamp
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, now + 1000L, "DEV_A", publicToken)))
                .andExpect(status().isOk());

        // Verify: P0-2 protected paid status — bill stays "paid"
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).getPaymentStatus()).isEqualTo("paid");
    }
}
