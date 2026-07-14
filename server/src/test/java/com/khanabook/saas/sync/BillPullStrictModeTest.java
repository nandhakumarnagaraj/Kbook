package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.utility.JwtUtility;
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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correction 1: in strict mode a null, blank, or whitespace terminal id must be rejected
 * for an OWNER bill pull. KBOOK_ADMIN and a valid trusted terminal remain allowed.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "terminal.sync.strict=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BillPullStrictModeTest extends BaseIntegrationTest {

	private static final Long RESTAURANT = 7501L;

	@Autowired private MockMvc mockMvc;
	@Autowired private RestaurantTerminalRepository terminalRepository;
	@Autowired private JwtUtility jwtUtility;
	@Autowired private ObjectMapper objectMapper;

	private String authToken() {
		return persistUserAndGetToken("owner-strict-" + UUID.randomUUID() + "@test.com", RESTAURANT, UserRole.OWNER);
	}

	private String adminToken() {
		return persistUserAndGetToken("admin-strict-" + UUID.randomUUID() + "@test.com", RESTAURANT, UserRole.KBOOK_ADMIN);
	}

	private String terminalToken(String id, String series) {
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
		String tid = id != null ? id : (t.getId() != null ? t.getId().toString() : series);
		return jwtUtility.generateTerminalToken("owner", RESTAURANT, "OWNER", tid, series, "DEV_" + series);
	}

	private void pullBillsAndExpect(String auth, String terminalToken, int expectedStatus) throws Exception {
		var builder = get("/sync/bills/pull")
				.header("Authorization", "Bearer " + auth)
				.param("lastSyncTimestamp", "0")
				.param("deviceId", "DEV_A")
				.param("ignoreDeviceId", "true");
		if (terminalToken != null) {
			builder.header("X-Terminal-Token", terminalToken);
		}
		mockMvc.perform(builder).andExpect(status().is(expectedStatus));
	}

	@Test
	void owner_pull_nullTerminal_strict_rejected() throws Exception {
		pullBillsAndExpect(authToken(), null, 400);
	}

	@Test
	void owner_pull_blankTerminal_strict_rejected() throws Exception {
		pullBillsAndExpect(authToken(), terminalToken("", "A"), 400);
	}

	@Test
	void owner_pull_whitespaceTerminal_strict_rejected() throws Exception {
		pullBillsAndExpect(authToken(), terminalToken("   ", "A"), 400);
	}

	@Test
	void admin_pull_nullTerminal_strict_allowed() throws Exception {
		pullBillsAndExpect(adminToken(), null, 200);
	}

	@Test
	void owner_pull_validTerminal_strict_allowed() throws Exception {
		pullBillsAndExpect(authToken(), terminalToken(null, "A"), 200);
	}
}
