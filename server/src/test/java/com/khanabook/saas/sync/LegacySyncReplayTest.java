package com.khanabook.saas.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.sync.dto.payload.BillDTO;
import com.khanabook.saas.sync.dto.payload.BillItemDTO;
import com.khanabook.saas.sync.dto.payload.BillPaymentDTO;
import com.khanabook.saas.sync.dto.payload.MasterSyncResponseDTO;
import com.khanabook.saas.utility.JwtUtility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 4.2: legacy sync replay. Plays a captured Base_Branch (v1) request set
 * from src/test/resources/legacy-sync/v1-push-fixture.json through the real
 * controller path (bill / bill-item / bill-payment pushes, then master pull)
 * and asserts the response conforms to the v1 response schema the Android app
 * consumes (MasterSyncResponseDTO and its payload DTOs).
 */
@AutoConfigureMockMvc
class LegacySyncReplayTest extends BaseIntegrationTest {

    private static final String FIXTURE = "legacy-sync/v1-push-fixture.json";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private JwtUtility jwtUtility;

    @Test
    void replayCapturedV1RequestSet_responseConformsToSchema() throws Exception {
        JsonNode fixture = objectMapper.readTree(new ClassPathResource(FIXTURE).getInputStream());
        Long restaurantId = fixture.get("restaurantId").asLong();
        String deviceId = fixture.get("deviceId").asText();

        String ownerToken = persistUserAndGetToken(
                "legacy-replay-" + UUID.randomUUID() + "@test.com", restaurantId, UserRole.OWNER);
        String terminalToken = terminalTokenFor(restaurantId);

        MvcResult pushBills = mockMvc.perform(post("/sync/bills/push")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("X-Terminal-Token", terminalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture.get("bills").toString()))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult pushItems = mockMvc.perform(post("/sync/bills/items/push")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("X-Terminal-Token", terminalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture.get("billItems").toString()))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult pushPayments = mockMvc.perform(post("/sync/bills/payments/push")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("X-Terminal-Token", terminalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture.get("billPayments").toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(pushBills.getResponse().getContentAsString())
                .as("push responses must be parseable JSON")
                .isNotNull();
        objectMapper.readTree(pushItems.getResponse().getContentAsString());
        objectMapper.readTree(pushPayments.getResponse().getContentAsString());

        MvcResult pull = mockMvc.perform(get("/sync/master/pull")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("X-Terminal-Token", terminalToken)
                        .param("lastSyncTimestamp", "0")
                        .param("deviceId", deviceId)
                        .param("restaurantId", restaurantId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String body = pull.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);

        assertSchemaConformance(root);
        assertRoundTripConformance(root, fixture);
    }

    private void assertSchemaConformance(JsonNode root) {
        assertThat(root.hasNonNull("serverTimestamp")).isTrue();
        assertThat(root.get("serverTimestamp").asLong()).isGreaterThan(0L);
        assertThat(root.has("profiles")).isTrue();
        assertThat(root.has("users")).isTrue();
        assertThat(root.has("categories")).isTrue();
        assertThat(root.has("menuItems")).isTrue();
        assertThat(root.has("itemVariants")).isTrue();
        assertThat(root.has("stockLogs")).isTrue();
        assertThat(root.has("bills")).isTrue();
        assertThat(root.has("billItems")).isTrue();
        assertThat(root.has("billPayments")).isTrue();
        assertThat(root.has("hasMore")).isTrue();
        assertThat(root.get("hasMore").isBoolean()).isTrue();
        if (root.get("hasMore").asBoolean()) {
            assertThat(root.hasNonNull("nextPage")).isTrue();
        } else {
            assertThat(root.has("nextPage")).isTrue();
        }
    }

    private void assertRoundTripConformance(JsonNode root, JsonNode fixture) {
        MasterSyncResponseDTO response = objectMapper.convertValue(root, MasterSyncResponseDTO.class);

        assertThat(response.getBills()).isNotEmpty();
        BillDTO bill901 = response.getBills().stream()
                .filter(b -> Long.valueOf(901L).equals(b.getLocalId()))
                .findFirst().orElseThrow(() -> new AssertionError("pushed bill localId=901 missing from pull"));
        BillDTO bill902 = response.getBills().stream()
                .filter(b -> Long.valueOf(902L).equals(b.getLocalId()))
                .findFirst().orElseThrow(() -> new AssertionError("pushed bill localId=902 missing from pull"));

        assertThat(bill901.getDeviceId()).isEqualTo("DEV_A");
        assertThat(bill901.getPaymentMode()).isEqualTo("cash");
        assertThat(bill901.getTotalAmount().doubleValue()).isEqualTo(150.00d);
        assertThat(bill901.getId()).isNotNull();
        assertThat(bill902.getPaymentMode()).isEqualTo("part_cash_upi");
        assertThat(bill902.getTotalAmount().doubleValue()).isEqualTo(270.00d);
        assertThat(bill902.getPartAmount2().doubleValue()).isEqualTo(140.00d);

        assertThat(response.getBillItems()).isNotEmpty();
        BillItemDTO item9011 = response.getBillItems().stream()
                .filter(i -> Long.valueOf(9011L).equals(i.getLocalId()))
                .findFirst().orElseThrow(() -> new AssertionError("pushed bill item localId=9011 missing from pull"));
        assertThat(item9011.getItemName()).isEqualTo("Idli");
        assertThat(item9011.getQuantity()).isEqualTo(2);
        assertThat(item9011.getId()).isNotNull();

        assertThat(response.getBillPayments()).isNotEmpty();
        BillPaymentDTO payment90202 = response.getBillPayments().stream()
                .filter(p -> Long.valueOf(90202L).equals(p.getLocalId()))
                .findFirst().orElseThrow(() -> new AssertionError("pushed bill payment localId=90202 missing from pull"));
        assertThat(payment90202.getPaymentMode()).isEqualTo("upi");
        assertThat(payment90202.getAmount().doubleValue()).isEqualTo(140.00d);
        assertThat(payment90202.getId()).isNotNull();

        assertThat(response.getServerTimestamp()).isGreaterThan(0L);
    }

    private String terminalTokenFor(Long restaurantId) {
        RestaurantTerminal t = terminalRepository.findByRestaurantIdAndTerminalSeries(restaurantId, "A")
                .orElseGet(() -> {
                    RestaurantTerminal nt = new RestaurantTerminal();
                    nt.setRestaurantId(restaurantId);
                    nt.setTerminalSeries("A");
                    nt.setTerminalName("Terminal A");
                    nt.setDeviceId("DEV_A");
                    nt.setIsActive(true);
                    nt.setCreatedAt(System.currentTimeMillis());
                    nt.setUpdatedAt(System.currentTimeMillis());
                    return terminalRepository.save(nt);
                });
        String id = t.getId() != null ? t.getId().toString() : "A";
        return jwtUtility.generateTerminalToken("owner", restaurantId, "OWNER", id, "A", "DEV_A");
    }
}
