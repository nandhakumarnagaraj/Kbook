package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.UserRole;
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
 * Correction 2 scenarios 6 & 7: with compatibility mode disabled, the legacy client
 * terminal id is no longer trusted — a missing token is rejected (400) and a supplied
 * terminal id is rejected (403) even though strict mode itself is off.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "terminal.sync.compatibility=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MasterSyncCompatDisabledTest extends BaseIntegrationTest {

	private static final Long RESTAURANT = 7801L;

	@Autowired private MockMvc mockMvc;

	private String authToken() {
		return persistUserAndGetToken("owner-ms-compat-" + UUID.randomUUID() + "@test.com", RESTAURANT, UserRole.OWNER);
	}

	@Test
	void missingToken_compatDisabled_rejected() throws Exception {
		mockMvc.perform(get("/sync/master/pull")
						.header("Authorization", "Bearer " + authToken())
						.param("lastSyncTimestamp", "0")
						.param("deviceId", "DEV_A")
						.param("ignoreDeviceId", "false"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void legacyQueryId_compatDisabled_rejected() throws Exception {
		mockMvc.perform(get("/sync/master/pull")
						.header("Authorization", "Bearer " + authToken())
						.param("lastSyncTimestamp", "0")
						.param("deviceId", "DEV_A")
						.param("ignoreDeviceId", "false")
						.param("terminalId", "A"))
				.andExpect(status().isForbidden());
	}
}
