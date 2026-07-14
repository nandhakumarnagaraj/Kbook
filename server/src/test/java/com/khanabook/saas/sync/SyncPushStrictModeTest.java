package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correction 2 compatibility-security: in strict mode, bill / bill-item / bill-payment
 * pushes without an X-Terminal-Token are rejected. KBOOK_ADMIN remains exempt.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "terminal.sync.strict=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SyncPushStrictModeTest extends BaseIntegrationTest {

	private static final Long RESTAURANT = 7901L;

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	private String authToken() {
		return persistUserAndGetToken("owner-push-strict-" + UUID.randomUUID() + "@test.com", RESTAURANT, UserRole.OWNER);
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

	@Test
	void billPush_withoutToken_strict_rejected() throws Exception {
		mockMvc.perform(post("/sync/bills/push")
						.header("Authorization", "Bearer " + authToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftBillJson(1, 1000)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void billItemPush_withoutToken_strict_rejected() throws Exception {
		String json = """
			[{
			  "localId": 1,
			  "deviceId": "DEV_A",
			  "restaurantId": %d,
			  "updatedAt": 1000,
			  "createdAt": 1000,
			  "isDeleted": false,
			  "billId": 1,
			  "menuItemId": 0,
			  "itemName": "Test",
			  "price": 10.00,
			  "quantity": 1,
			  "itemTotal": 10.00
			}]
			""".formatted(RESTAURANT);
		mockMvc.perform(post("/sync/bills/items/push")
						.header("Authorization", "Bearer " + authToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	void billPaymentPush_withoutToken_strict_rejected() throws Exception {
		String json = """
			[{
			  "localId": 1,
			  "deviceId": "DEV_A",
			  "restaurantId": %d,
			  "updatedAt": 1000,
			  "createdAt": 1000,
			  "isDeleted": false,
			  "billId": 1,
			  "paymentMode": "cash",
			  "amount": 10.00
			}]
			""".formatted(RESTAURANT);
		mockMvc.perform(post("/sync/bills/payments/push")
						.header("Authorization", "Bearer " + authToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}
}
