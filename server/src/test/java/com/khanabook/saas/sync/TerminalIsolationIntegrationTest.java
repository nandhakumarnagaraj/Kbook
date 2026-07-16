package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.utility.JwtUtility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security regression tests for multi-terminal bill ownership isolation (defects D1/D2/D3):
 *  - D1: terminal identity must come from the X-Terminal-Token, not the client body.
 *  - D2: a different terminal cannot overwrite or replay another terminal's bill via publicToken.
 *  - D3: pull returns only the calling terminal's operational bills plus restaurant-wide finalized bills.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TerminalIsolationIntegrationTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 7401L;

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository billRepository;
    @Autowired private BillItemRepository billItemRepository;
    @Autowired private BillPaymentRepository billPaymentRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private JwtUtility jwtUtility;
    @Autowired private ObjectMapper objectMapper;

    private String authToken() {
        String email = "owner-iso-" + UUID.randomUUID() + "@test.com";
        return persistUserAndGetToken(email, RESTAURANT, UserRole.OWNER);
    }

    private String terminalToken(String series) {
        RestaurantTerminal t = terminalRepository.findByRestaurantIdAndTerminalSeries(RESTAURANT, series)
                .orElseGet(() -> {
                    RestaurantTerminal nt = new RestaurantTerminal();
                    nt.setRestaurantId(RESTAURANT);
                    nt.setTerminalSeries(series);
                    nt.setTerminalName("Terminal " + series);
                    nt.setDeviceId("DEV_" + series);
                    nt.setIsActive(true);
                    nt.setCreatedAt(System.currentTimeMillis());
                    nt.setUpdatedAt(System.currentTimeMillis());
                    return terminalRepository.save(nt);
                });
        String id = t.getId() != null ? t.getId().toString() : series;
        return jwtUtility.generateTerminalToken("owner", RESTAURANT, "OWNER", id, series, "DEV_" + series);
    }

    private String draftBillJson(long localId, long updatedAt, String publicToken) {
        String tokenLine = publicToken == null ? "" : "\"publicToken\": \"" + publicToken + "\",\n";
        return """
            [{
              "localId": %d,
              "deviceId": "DEV_A",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              %s
              "dailyOrderId": 1,
              "dailyOrderDisplay": "1",
              "lifetimeOrderId": 1,
              "orderType": "dine-in",
              "subtotal": 100.00,
              "totalAmount": 100.00,
              "paymentMode": "cash",
              "paymentStatus": "unpaid",
              "orderStatus": "draft"
            }]
            """.formatted(localId, RESTAURANT, updatedAt, updatedAt, tokenLine);
    }

    private String completedBillJson(long localId, long updatedAt, String publicToken) {
        return """
            [{
              "localId": %d,
              "deviceId": "DEV_A",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              "publicToken": "%s",
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
            """.formatted(localId, RESTAURANT, updatedAt, updatedAt, publicToken);
    }

    private UUID publicTokenOf(long localId) {
        return billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT, "DEV_A", localId)
                .orElseThrow().getPublicToken();
    }

    @Test
    void push_fromTerminalB_cannotOverwriteTerminalA_bill_byPublicToken() throws Exception {
        String auth = authToken();
        String tokenA = terminalToken("A");
        String tokenB = terminalToken("B");

        // Terminal A creates a draft bill.
        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBillJson(1, 1000, null)))
                .andExpect(status().isOk());
        UUID token = publicTokenOf(1);

        // Terminal B tries to overwrite A's bill with a newer timestamp (replay/overwrite).
        String replay = draftBillJson(1, 2000, token.toString());
        String body = mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replay))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode resp = objectMapper.readTree(body);
        assertThat(resp.get("failedLocalIds").size()).isGreaterThanOrEqualTo(1);
        assertThat(resp.get("successfulLocalIds").size()).isEqualTo(0);

        // The server bill must remain owned by A and unchanged (still draft, old timestamp).
        Bill server = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT, "DEV_A", 1L).orElseThrow();
        assertThat(server.getCurrentOwnerTerminalId()).isEqualTo(terminalRepository
                .findByRestaurantIdAndTerminalSeries(RESTAURANT, "A").orElseThrow().getId().toString());
        assertThat(server.getUpdatedAt()).isEqualTo(1000L);
        assertThat(server.getOrderStatus()).isEqualTo("draft");
    }

    @Test
    void pull_returnsOnlyOwningTerminalsDraftButAllFinalizedBills() throws Exception {
        String auth = authToken();
        String tokenA = terminalToken("A");
        String tokenB = terminalToken("B");

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBillJson(1, 1000, null)))
                .andExpect(status().isOk());
        UUID token = publicTokenOf(1);

        // Terminal A can see its own draft.
        JsonNode pullA = pullAs(auth, tokenA);
        assertThat(containsPublicToken(pullA, token)).isTrue();

        // Terminal B cannot see A's draft bill.
        JsonNode pullB = pullAs(auth, tokenB);
        assertThat(containsPublicToken(pullB, token)).isFalse();

        // A completes the bill -> finalized bills are visible restaurant-wide.
        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedBillJson(1, 3000, token.toString())))
                .andExpect(status().isOk());

        JsonNode pullBAfter = pullAs(auth, tokenB);
        assertThat(containsPublicToken(pullBAfter, token)).isTrue();
    }

    private JsonNode pullAs(String auth, String terminalToken) throws Exception {
        String body = mockMvc.perform(get("/sync/bills/pull")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", terminalToken)
                        .param("lastSyncTimestamp", "0")
                        .param("deviceId", "DEV_A")
                        .param("ignoreDeviceId", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private boolean containsPublicToken(JsonNode array, UUID token) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode node : array) {
            JsonNode pt = node.get("publicToken");
            if (pt != null && pt.asText().equals(token.toString())) {
                return true;
            }
        }
        return false;
    }

    // ── Child-record (BillItem / BillPayment) terminal isolation ──────────────────

    private String billItemJson(long localId, long updatedAt, long billLocalId) {
        return """
            [{
              "localId": %d,
              "deviceId": "DEV_A",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              "billId": %d,
              "menuItemId": 0,
              "itemName": "Test item",
              "price": 10.00,
              "quantity": 1,
              "itemTotal": 10.00
            }]
            """.formatted(localId, RESTAURANT, updatedAt, updatedAt, billLocalId);
    }

    private String billPaymentJson(long localId, long updatedAt, long billLocalId) {
        return """
            [{
              "localId": %d,
              "deviceId": "DEV_A",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              "billId": %d,
              "paymentMode": "cash",
              "amount": 10.00
            }]
            """.formatted(localId, RESTAURANT, updatedAt, updatedAt, billLocalId);
    }

    private Long serverBillIdOf(long localId) {
        return billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT, "DEV_A", localId)
                .orElseThrow().getId();
    }

    @Test
    void push_billItem_fromTerminalB_rejectedOnTerminalA_bill() throws Exception {
        String auth = authToken();
        String tokenA = terminalToken("A");
        String tokenB = terminalToken("B");

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBillJson(1, 1000, null)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/sync/bills/items/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billItemJson(1, 5000, 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode resp = objectMapper.readTree(body);
        assertThat(resp.get("failedLocalIds").size()).isGreaterThanOrEqualTo(1);
        assertThat(resp.get("successfulLocalIds").size()).isEqualTo(0);

        // No item row should have been written against A's bill.
        assertThat(billItemRepository.findByRestaurantIdAndServerBillIdInAndServerUpdatedAtGreaterThan(
                RESTAURANT, List.of(serverBillIdOf(1L)), 0L)).isEmpty();
    }

    @Test
    void push_billItem_fromTerminalA_acceptedOnOwnBill() throws Exception {
        String auth = authToken();
        String tokenA = terminalToken("A");

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBillJson(1, 1000, null)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/sync/bills/items/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billItemJson(1, 5000, 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode resp = objectMapper.readTree(body);
        assertThat(resp.get("failedLocalIds").size()).isEqualTo(0);
        assertThat(resp.get("successfulLocalIds").size()).isEqualTo(1);
    }

    @Test
    void push_billPayment_fromTerminalB_rejectedOnTerminalA_bill() throws Exception {
        String auth = authToken();
        String tokenA = terminalToken("A");
        String tokenB = terminalToken("B");

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBillJson(1, 1000, null)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/sync/bills/payments/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billPaymentJson(1, 5000, 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode resp = objectMapper.readTree(body);
        assertThat(resp.get("failedLocalIds").size()).isGreaterThanOrEqualTo(1);
        assertThat(resp.get("successfulLocalIds").size()).isEqualTo(0);

        assertThat(billPaymentRepository.findByRestaurantIdAndServerBillIdInAndServerUpdatedAtGreaterThan(
                RESTAURANT, List.of(serverBillIdOf(1L)), 0L)).isEmpty();
    }

    @Test
    void push_billPayment_fromTerminalA_acceptedOnOwnBill() throws Exception {
        String auth = authToken();
        String tokenA = terminalToken("A");

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBillJson(1, 1000, null)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/sync/bills/payments/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billPaymentJson(1, 5000, 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode resp = objectMapper.readTree(body);
        assertThat(resp.get("failedLocalIds").size()).isEqualTo(0);
        assertThat(resp.get("successfulLocalIds").size()).isEqualTo(1);
    }

    // ── Dine-in "settle draft" flow: complete the bill, then push its payment ──────
    // Regression for the deadlock where the owning terminal's payment was rejected
    // because completing the order had already marked the parent bill finalized.

    @Test
    void push_billPayment_fromOwningTerminal_acceptedAfterBillCompleted() throws Exception {
        String auth = authToken();
        String tokenA = terminalToken("A");

        // 1. Terminal A creates the dine-in draft (already synced to the server).
        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBillJson(1, 1000, null)))
                .andExpect(status().isOk());
        UUID token = publicTokenOf(1);

        // 2. Completing the payment pushes the bill update first -> bill becomes finalized.
        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedBillJson(1, 3000, token.toString())))
                .andExpect(status().isOk());

        // 3. The payment for that now-finalized bill must still be accepted from its owner.
        String body = mockMvc.perform(post("/sync/bills/payments/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billPaymentJson(1, 5000, 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode resp = objectMapper.readTree(body);
        assertThat(resp.get("failedLocalIds").size()).isEqualTo(0);
        assertThat(resp.get("successfulLocalIds").size()).isEqualTo(1);

        // The payment row is persisted against A's bill.
        assertThat(billPaymentRepository.findByRestaurantIdAndServerBillIdInAndServerUpdatedAtGreaterThan(
                RESTAURANT, List.of(serverBillIdOf(1L)), 0L)).isNotEmpty();
    }

    @Test
    void push_billPayment_fromOtherTerminal_stillRejectedAfterBillCompleted() throws Exception {
        String auth = authToken();
        String tokenA = terminalToken("A");
        String tokenB = terminalToken("B");

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBillJson(1, 1000, null)))
                .andExpect(status().isOk());
        UUID token = publicTokenOf(1);

        mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedBillJson(1, 3000, token.toString())))
                .andExpect(status().isOk());

        // A DIFFERENT terminal must NOT be able to attach a payment to A's finalized bill.
        String body = mockMvc.perform(post("/sync/bills/payments/push")
                        .header("Authorization", "Bearer " + auth)
                        .header("X-Terminal-Token", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billPaymentJson(1, 5000, 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode resp = objectMapper.readTree(body);
        assertThat(resp.get("failedLocalIds").size()).isGreaterThanOrEqualTo(1);
        assertThat(resp.get("successfulLocalIds").size()).isEqualTo(0);

        assertThat(billPaymentRepository.findByRestaurantIdAndServerBillIdInAndServerUpdatedAtGreaterThan(
                RESTAURANT, List.of(serverBillIdOf(1L)), 0L)).isEmpty();
    }
}
