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
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correction 2: MasterSync terminal identity is server-authoritative (X-Terminal-Token).
 * Default profile = compatibility mode enabled, strict disabled (rollout state).
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MasterSyncTerminalIsolationTest extends BaseIntegrationTest {

	private static final Long RESTAURANT = 7601L;

	@Autowired private MockMvc mockMvc;
	@Autowired private RestaurantTerminalRepository terminalRepository;
	@Autowired private BillRepository billRepository;
	@Autowired private JwtUtility jwtUtility;
	@Autowired private ObjectMapper objectMapper;

	private String authToken() {
		return persistUserAndGetToken("owner-ms-" + UUID.randomUUID() + "@test.com", RESTAURANT, UserRole.OWNER);
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

	private String terminalIdOf(String token) {
		return jwtUtility.extractTerminalId(token);
	}

	private String draftBillJson(long localId, long updatedAt) {
		return """
			[{
			  "localId": %d,
			  "deviceId": "DEV_A",
			  "restaurantId": %d,
			  "updatedAt": %d,
			  "createdAt": %d,
			  "isDeleted": false,
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
			""".formatted(localId, RESTAURANT, updatedAt, updatedAt);
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

	private void pushBill(String token, String json) throws Exception {
		mockMvc.perform(post("/sync/bills/push")
						.header("Authorization", "Bearer " + authToken())
						.header("X-Terminal-Token", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isOk());
	}

	private void pushItem(String token, String json) throws Exception {
		mockMvc.perform(post("/sync/bills/items/push")
						.header("Authorization", "Bearer " + authToken())
						.header("X-Terminal-Token", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isOk());
	}

	private JsonNode masterPull(String token, String queryTerminalId) throws Exception {
		var builder = get("/sync/master/pull")
				.header("Authorization", "Bearer " + authToken())
				.header("X-Terminal-Token", token)
				.param("lastSyncTimestamp", "0")
				.param("deviceId", "DEV_A")
				.param("ignoreDeviceId", "false");
		if (queryTerminalId != null) {
			builder.param("terminalId", queryTerminalId);
		}
		String body = mockMvc.perform(builder)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body);
	}

	private boolean containsPublicToken(JsonNode array, String token) {
		if (array == null || !array.isArray()) return false;
		for (JsonNode n : array) {
			JsonNode pt = n.get("publicToken");
			if (pt != null && pt.asText().equals(token)) return true;
		}
		return false;
	}

	@Test
	void terminalB_token_withTerminalA_queryId_rejected() throws Exception {
		String tokenA = terminalToken("A");
		String tokenB = terminalToken("B");
		mockMvc.perform(get("/sync/master/pull")
						.header("Authorization", "Bearer " + authToken())
						.header("X-Terminal-Token", tokenB)
						.param("lastSyncTimestamp", "0")
						.param("deviceId", "DEV_A")
						.param("ignoreDeviceId", "false")
						.param("terminalId", terminalIdOf(tokenA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void terminalB_token_withNoQueryId_usesTerminalB() throws Exception {
		String tokenA = terminalToken("A");
		String tokenB = terminalToken("B");
		pushBill(tokenA, draftBillJson(1, 1000));
		String token = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT, "DEV_A", 1L)
				.orElseThrow().getPublicToken().toString();
		JsonNode pull = masterPull(tokenB, null);
		assertThat(containsPublicToken(pull.get("bills"), token)).isFalse();
	}

	@Test
	void terminalB_token_withMatchingQueryId_succeeds() throws Exception {
		String tokenB = terminalToken("B");
		mockMvc.perform(get("/sync/master/pull")
						.header("Authorization", "Bearer " + authToken())
						.header("X-Terminal-Token", tokenB)
						.param("lastSyncTimestamp", "0")
						.param("deviceId", "DEV_A")
						.param("ignoreDeviceId", "false")
						.param("terminalId", terminalIdOf(tokenB)))
				.andExpect(status().isOk());
	}

	@Test
	void terminalA_activeBill_notReturnedToB() throws Exception {
		String tokenA = terminalToken("A");
		String tokenB = terminalToken("B");
		pushBill(tokenA, draftBillJson(1, 1000));
		String token = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT, "DEV_A", 1L)
				.orElseThrow().getPublicToken().toString();
		JsonNode pull = masterPull(tokenB, null);
		assertThat(containsPublicToken(pull.get("bills"), token)).isFalse();
	}

	@Test
	void completedHistory_visibleRestaurantWide() throws Exception {
		String tokenA = terminalToken("A");
		String tokenB = terminalToken("B");
		pushBill(tokenA, draftBillJson(1, 1000));
		String token = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(RESTAURANT, "DEV_A", 1L)
				.orElseThrow().getPublicToken().toString();
		pushBill(tokenA, completedBillJson(1, 3000, token));
		JsonNode pull = masterPull(tokenB, null);
		assertThat(containsPublicToken(pull.get("bills"), token)).isTrue();
	}

	@Test
	void itemsFollowEffectiveTerminalScope() throws Exception {
		String tokenA = terminalToken("A");
		String tokenB = terminalToken("B");
		pushBill(tokenA, draftBillJson(1, 1000));
		pushItem(tokenA, billItemJson(1, 2000, 1));
		JsonNode pull = masterPull(tokenB, null);
		assertThat(pull.get("billItems").isArray()).isTrue();
		assertThat(pull.get("billItems").size()).isEqualTo(0);
	}

	@Test
	void legacyFallback_queryTerminalId_allowedWhenCompatEnabled() throws Exception {
		// No X-Terminal-Token, but a legacy client supplies a terminal id query param.
		// While compatibility mode is enabled this is honoured (temporary).
		mockMvc.perform(get("/sync/master/pull")
						.header("Authorization", "Bearer " + authToken())
						.param("lastSyncTimestamp", "0")
						.param("deviceId", "DEV_A")
						.param("ignoreDeviceId", "false")
						.param("terminalId", "A"))
				.andExpect(status().isOk());
	}
}
