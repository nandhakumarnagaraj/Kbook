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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correction 2 scenarios 4 & 5: with strict mode enabled, a missing or blank terminal
 * identity (no X-Terminal-Token, blank query id) must be rejected for master pull.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "terminal.sync.strict=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MasterSyncStrictModeTest extends BaseIntegrationTest {

	private static final Long RESTAURANT = 7701L;

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	private String authToken() {
		return persistUserAndGetToken("owner-ms-strict-" + UUID.randomUUID() + "@test.com", RESTAURANT, UserRole.OWNER);
	}

	private void masterPull(String queryTerminalId) throws Exception {
		var builder = get("/sync/master/pull")
				.header("Authorization", "Bearer " + authToken())
				.param("lastSyncTimestamp", "0")
				.param("deviceId", "DEV_A")
				.param("ignoreDeviceId", "false");
		if (queryTerminalId != null) {
			builder.param("terminalId", queryTerminalId);
		}
		mockMvc.perform(builder).andExpect(status().isOk());
	}

	@Test
	void missingToken_strictMode_rejected() throws Exception {
		mockMvc.perform(get("/sync/master/pull")
						.header("Authorization", "Bearer " + authToken())
						.param("lastSyncTimestamp", "0")
						.param("deviceId", "DEV_A")
						.param("ignoreDeviceId", "false"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void blankQueryId_strictMode_rejected() throws Exception {
		mockMvc.perform(get("/sync/master/pull")
						.header("Authorization", "Bearer " + authToken())
						.param("lastSyncTimestamp", "0")
						.param("deviceId", "DEV_A")
						.param("ignoreDeviceId", "false")
						.param("terminalId", ""))
				.andExpect(status().isBadRequest());
	}
}
