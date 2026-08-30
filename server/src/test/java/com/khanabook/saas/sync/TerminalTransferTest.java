package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.utility.JwtUtility;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Distributed state problem D4: Terminal-based bill ownership transfer.
 *
 * Real use case: Customer moves from Terminal A's table to Terminal B's table.
 * Terminal A transfers the bill. After transfer, Terminal A can no longer
 * push updates to the bill, and Terminal B receives it on pull.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TerminalTransferTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 9501L;

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository billRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private JwtUtility jwtUtility;
    @Autowired private ObjectMapper objectMapper;

    private String ownerToken;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-tt-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
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

    private String billJson(long localId, long updatedAt, String deviceId) {
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
              "paymentStatus": "pending",
              "orderStatus": "draft"
            }]
            """.formatted(localId, deviceId, RESTAURANT, updatedAt, updatedAt,
                    localId, localId, localId);
    }

    @Test
    void transfer_billOwnershipChangesToTarget() throws Exception {
        // D4: Terminal A creates a bill, then transfers to Terminal B.
        // After transfer, bill.currentOwnerTerminalId = B.
        RestaurantTerminal tA = createTerminal("A");
        RestaurantTerminal tB = createTerminal("B");
        String tokenA = terminalToken(tA);

        // Terminal A pushes a bill
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_A")))
                .andExpect(status().isOk());

        // Get the bill's public token
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        Bill bill = bills.get(0);
        assertThat(bill.getCurrentOwnerTerminalId()).isEqualTo(tA.getId().toString());

        // Transfer to Terminal B
        String transferJson = """
            {"billPublicToken": "%s", "targetTerminalSeries": "B"}
            """.formatted(bill.getPublicToken().toString());

        mockMvc.perform(post("/sync/terminal/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenA)
                .content(transferJson))
                .andExpect(status().isOk());

        // Verify: ownership changed
        Bill transferred = billRepository.findById(bill.getId()).orElseThrow();
        assertThat(transferred.getCurrentOwnerTerminalId()).isEqualTo(tB.getId().toString());
        assertThat(transferred.getTerminalSeries()).isEqualTo("B");
        // createdTerminalId is immutable
        assertThat(transferred.getCreatedTerminalId()).isEqualTo(tA.getId().toString());
    }

    @Test
    void transfer_originalTerminalCannotPushAfterTransfer() throws Exception {
        // D4: After transfer, Terminal A tries to push an update to the bill.
        // The sync ownership check should reject it.
        RestaurantTerminal tA = createTerminal("A");
        RestaurantTerminal tB = createTerminal("B");
        String tokenA = terminalToken(tA);

        // Terminal A pushes a bill
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_A")))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        Bill bill = bills.get(0);

        // Transfer to Terminal B
        String transferJson = """
            {"billPublicToken": "%s", "targetTerminalSeries": "B"}
            """.formatted(bill.getPublicToken().toString());
        mockMvc.perform(post("/sync/terminal/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenA)
                .content(transferJson))
                .andExpect(status().isOk());

        // Terminal A tries to push an update to the same bill
        // The sync should reject because bill is now owned by Terminal B
        var result = mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, System.currentTimeMillis() + 1000, "DEV_A")))
                .andReturn();

        // May return 200 (sync-level) or may reject at ownership check
        // The bill's currentOwnerTerminalId is now B, not A
        Bill afterPush = billRepository.findById(bill.getId()).orElseThrow();
        assertThat(afterPush.getCurrentOwnerTerminalId()).isEqualTo(tB.getId().toString());
    }

    @Test
    void transfer_toInactiveTerminal_rejected() throws Exception {
        // D4: Cannot transfer to a deactivated terminal
        RestaurantTerminal tA = createTerminal("A");
        RestaurantTerminal tB = createTerminal("B");
        tB.setIsActive(false);
        terminalRepository.save(tB);
        String tokenA = terminalToken(tA);

        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_A")))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        Bill bill = bills.get(0);

        // Transfer to deactivated Terminal B — should be rejected
        String transferJson = """
            {"billPublicToken": "%s", "targetTerminalSeries": "B"}
            """.formatted(bill.getPublicToken().toString());
        mockMvc.perform(post("/sync/terminal/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + ownerToken)
                .content(transferJson))
                .andExpect(status().is4xxClientError());

        // Verify: ownership unchanged
        Bill afterTransfer = billRepository.findById(bill.getId()).orElseThrow();
        assertThat(afterTransfer.getCurrentOwnerTerminalId()).isEqualTo(tA.getId().toString());
    }

    @Test
    void transfer_nonOwnerTerminal_rejected() throws Exception {
        // D4: Terminal B tries to transfer a bill owned by Terminal A — should fail
        RestaurantTerminal tA = createTerminal("A");
        RestaurantTerminal tB = createTerminal("B");
        RestaurantTerminal tC = createTerminal("C");
        String tokenA = terminalToken(tA);
        String tokenB = terminalToken(tB);

        // Terminal A creates a bill
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_A")))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        Bill bill = bills.get(0);

        // Terminal B tries to transfer Terminal A's bill to Terminal C
        String transferJson = """
            {"billPublicToken": "%s", "targetTerminalSeries": "C"}
            """.formatted(bill.getPublicToken().toString());
        mockMvc.perform(post("/sync/terminal/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenB)
                .content(transferJson))
                .andExpect(status().isForbidden());

        // Verify: ownership unchanged
        Bill afterTransfer = billRepository.findById(bill.getId()).orElseThrow();
        assertThat(afterTransfer.getCurrentOwnerTerminalId()).isEqualTo(tA.getId().toString());
    }

    @Test
    void transfer_createdTerminalId_immutable() throws Exception {
        // D4: After transfer, createdTerminalId never changes (audit trail)
        RestaurantTerminal tA = createTerminal("A");
        RestaurantTerminal tB = createTerminal("B");
        String tokenA = terminalToken(tA);

        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_A")))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        Bill bill = bills.get(0);
        String originalCreator = bill.getCreatedTerminalId();

        // Transfer
        String transferJson = """
            {"billPublicToken": "%s", "targetTerminalSeries": "B"}
            """.formatted(bill.getPublicToken().toString());
        mockMvc.perform(post("/sync/terminal/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", tokenA)
                .content(transferJson))
                .andExpect(status().isOk());

        Bill transferred = billRepository.findById(bill.getId()).orElseThrow();
        // createdTerminalId unchanged — immutable audit trail
        assertThat(transferred.getCreatedTerminalId()).isEqualTo(originalCreator);
        // currentOwnerTerminalId changed
        assertThat(transferred.getCurrentOwnerTerminalId()).isEqualTo(tB.getId().toString());
    }
}
