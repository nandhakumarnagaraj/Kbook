package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.auth.controller.AuthController.AuthResponse;
import com.khanabook.saas.feature.auth.controller.AuthController.LoginRequest;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.service.AuthServiceImpl;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression cover for the duplicate restaurantprofiles incident: a restaurant that held
 * TWO profile rows could not log in at all (HTTP 500 instead of 400), and every new signup
 * created another pair.
 *
 * <p>Incident chain: signup writes the stub with local_id = 1 while the app pushes its one
 * local profile keyed on local_id = restaurant_id, so the push inserted a second row; the
 * single-row readers then threw IncorrectResultSizeDataAccessException, which
 * GlobalExceptionHandler turned into a 500 on login (correct password only - a wrong
 * password returned a clean 400 first).
 *
 * <p>The invariant these tests lock in:
 * <ol>
 *   <li>a profile push resolves by restaurant_id, so it updates the existing row instead of
 *       inserting a second one;</li>
 *   <li>the single-row readers pick one row deterministically instead of throwing, so a
 *       restaurant with a legacy duplicate stays usable;</li>
 *   <li>suspension is checked across every row, so a duplicate cannot smuggle a suspended
 *       business back in;</li>
 *   <li>the database refuses a second row for a restaurant.</li>
 * </ol>
 *
 * <p>NOT transactional on purpose: these tests issue DDL (dropping and restoring the unique
 * constraint) to simulate legacy duplicate data that predates the constraint. DDL commits
 * implicitly in H2, which would silently defeat rollback-based cleanup, so each test cleans
 * up its own rows and restores the constraint in {@code @AfterEach}.
 */
@AutoConfigureMockMvc
class RestaurantProfileSingletonIntegrationTest extends BaseIntegrationTest {

	private static final String CONSTRAINT = "uq_restaurantprofiles_restaurant_id";

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private AuthServiceImpl authService;

	private final List<Long> touchedRestaurants = new java.util.ArrayList<>();

	@AfterEach
	void restoreSchemaAndCleanUp() {
		for (Long restaurantId : touchedRestaurants) {
			restaurantProfileRepository.findAllByRestaurantIdOrderByIdAsc(restaurantId)
					.forEach(restaurantProfileRepository::delete);
		}
		// Flush before the DDL below: JdbcTemplate does not trigger a Hibernate flush, and
		// H2 re-checks the table when the constraint is added.
		restaurantProfileRepository.flush();
		touchedRestaurants.clear();
		singletonConstraintPresent();
	}

	// ── 1. Root cause: the push must not create a second row ───────────────────

	/**
	 * The exact shape of the incident: the server already holds the signup row (local_id = 1)
	 * and the device pushes the same profile with localId = restaurantId.
	 *
	 * <p>Before the fix this inserted a second row (the composite unique key on
	 * (restaurant_id, device_id, local_id) does not see the two tuples as equal), which is
	 * what locked the restaurant out of login.
	 */
	@Test
	void profilePush_withLocalIdEqualToRestaurantId_updatesSignupRowWithoutInserting() throws Exception {
		Long restaurantId = freshRestaurantId();
		authTokenFor(restaurantId);
		assertThat(restaurantProfileRepository.findAllByRestaurantIdOrderByIdAsc(restaurantId))
				.as("signup leaves exactly one stub row")
				.hasSize(1);
		Long stubRowId = restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow().getId();

		mockMvc.perform(post("/sync/restaurantprofile/push")
						.header("Authorization", "Bearer " + authTokenFor(restaurantId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(profilePushJson(restaurantId, restaurantId, "Saket Cafe", "INR", "9867545806")))
				.andExpect(status().isOk());

		List<RestaurantProfile> rows = restaurantProfileRepository.findAllByRestaurantIdOrderByIdAsc(restaurantId);
		assertThat(rows).as("the push must land on the signup row, not fork a new one").hasSize(1);
		assertThat(rows.get(0).getId()).as("same physical row, so the device keeps its id").isEqualTo(stubRowId);
		assertThat(rows.get(0).getCurrency()).isEqualTo("INR");
		assertThat(rows.get(0).getWhatsappNumber()).isEqualTo("9867545806");
		assertThat(rows.get(0).getShopName()).isEqualTo("Saket Cafe");
	}

	/**
	 * local_id is the device's own key for its profile row and is left untouched on purpose:
	 * the app acknowledges the push with the localId it sent (markAsSynced / updateServerIdByLocalId),
	 * so rewriting it to 1 server-side would leave the profile permanently unsynced and
	 * re-pushed forever. The identity that matters is restaurant_id.
	 */
	@Test
	void profilePush_doesNotRewriteLocalId() throws Exception {
		Long restaurantId = freshRestaurantId();
		String token = authTokenFor(restaurantId);

		mockMvc.perform(post("/sync/restaurantprofile/push")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(profilePushJson(restaurantId, restaurantId, "Saket Cafe", "INR", "9867545806")))
				.andExpect(status().isOk());

		RestaurantProfile row = restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow();
		assertThat(row.getLocalId()).isEqualTo(restaurantId);
	}

	// ── 2. Legacy duplicates must not crash the readers ────────────────────────

	/**
	 * Before the fix this threw IncorrectResultSizeDataAccessException ("Query did not return
	 * a unique result: 2 results were returned") - the 500 on login.
	 */
	@Test
	void findByRestaurantId_returnsOneRow_whenLegacyDuplicateExists() {
		Long restaurantId = freshRestaurantId();
		authTokenFor(restaurantId);
		insertLegacyDuplicateStub(restaurantId);

		assertThatCode(() -> restaurantProfileRepository.findByRestaurantId(restaurantId))
				.as("a legacy duplicate must not throw any more")
				.doesNotThrowAnyException();

		// Newest row wins: signup always inserts the bare stub first and the device push
		// lands on the higher id, so the newest row is the one carrying the business data.
		RestaurantProfile resolved = restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow();
		assertThat(resolved.getCurrency()).as("readers get the data-bearing row").isEqualTo("INR");
		assertThat(resolved.getLocalId()).isEqualTo(restaurantId);
	}

	/**
	 * The other single-row reader: the PESSIMISTIC_WRITE lock used as the parent-row lock for
	 * the 5-terminal limit. It threw on duplicates too, failing terminal approval with a 500
	 * inside a transaction that has to stay atomic.
	 */
	@Test
	@Transactional // PESSIMISTIC_WRITE is only legal inside a transaction, like its callers
	void findAndLockByRestaurantId_locksOneRow_whenLegacyDuplicateExists() {
		Long restaurantId = freshRestaurantId();
		authTokenFor(restaurantId);
		insertLegacyDuplicateStub(restaurantId);

		assertThat(restaurantProfileRepository.findAndLockByRestaurantId(restaurantId))
				.as("terminal approval must not fail on a legacy duplicate")
				.isPresent();
	}

	/**
	 * The reported symptom end to end: login for a restaurant holding two profile rows must
	 * succeed. A wrong password still returns its own error - the incident was that the
	 * CORRECT password 500'd.
	 */
	@Test
	void login_succeeds_whenRestaurantHasLegacyDuplicateRows() {
		Long restaurantId = freshRestaurantId();
		String loginId = "9" + (100000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 800000000L));
		persistUser(loginId, restaurantId, UserRole.OWNER);
		insertLegacyDuplicateStub(restaurantId);

		LoginRequest request = new LoginRequest();
		request.setLoginId(loginId);
		request.setPassword("pass123");
		request.setDeviceId("TEST_DEVICE");

		AuthResponse response = authService.login(request);

		assertThat(response.getToken()).isNotBlank();
		assertThat(response.getRestaurantId()).isEqualTo(restaurantId);
	}

	// ── 3. Suspension must fail closed across duplicate rows ──────────────────

	/**
	 * Suspension is enforced on login and on every authenticated request. Deciding it from
	 * whichever single row a reader happened to get would let a suspended business back in
	 * through its other row.
	 */
	@Test
	void suspension_isSeenOnTheOlderRow_too() {
		Long restaurantId = freshRestaurantId();
		authTokenFor(restaurantId);
		insertLegacyDuplicateStub(restaurantId);

		// Suspend ONLY the older (legacy stub) row, which is not the row the reader returns.
		RestaurantProfile older = restaurantProfileRepository.findAllByRestaurantIdOrderByIdAsc(restaurantId).get(0);
		assertThat(restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow().getIsSuspended())
				.as("precondition: the reader sees the other, unsuspended row")
				.isFalse();

		older.setIsSuspended(true);
		restaurantProfileRepository.save(older);

		assertThat(restaurantProfileRepository.existsByRestaurantIdAndIsSuspendedTrue(restaurantId))
				.as("a suspended row anywhere in the restaurant must keep the business suspended")
				.isTrue();
	}

	// ── 4. The database must refuse a second row ──────────────────────────────

	@Test
	void secondProfileRowForTheSameRestaurant_isRejectedByTheDatabase() {
		Long restaurantId = freshRestaurantId();
		authTokenFor(restaurantId);
		singletonConstraintPresent();

		assertThatThrownBy(() -> {
			RestaurantProfile second = new RestaurantProfile();
			second.setRestaurantId(restaurantId);
			second.setLocalId(restaurantId);
			second.setDeviceId("SOME_OTHER_DEVICE");
			second.setShopName("Second row");
			second.setCreatedAt(System.currentTimeMillis());
			second.setUpdatedAt(System.currentTimeMillis());
			second.setServerUpdatedAt(System.currentTimeMillis());
			restaurantProfileRepository.saveAndFlush(second);
		})
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private Long freshRestaurantId() {
		Long id = 9_000_000_000_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
		touchedRestaurants.add(id);
		return id;
	}

	private String authTokenFor(Long restaurantId) {
		return persistUserAndGetToken("owner-" + UUID.randomUUID() + "@test.com", restaurantId, UserRole.OWNER);
	}

	/**
	 * Recreates the production duplicate: the server's bare signup stub (local_id = 1, no
	 * currency, no whatsapp) sitting next to the row the device pushed. The composite unique
	 * key let both exist because their tuples differ in local_id - which is the whole defect.
	 * Needs the singleton constraint out of the way, because such data predates it.
	 */
	private void insertLegacyDuplicateStub(Long restaurantId) {
		dropSingletonConstraint();
		RestaurantProfile stub = new RestaurantProfile();
		stub.setRestaurantId(restaurantId);
		stub.setLocalId(1L);
		stub.setDeviceId("DEV_SIGNUP");
		stub.setShopName("Test Shop " + restaurantId + "'s Restaurant");
		stub.setCreatedAt(System.currentTimeMillis() - 60_000);
		stub.setUpdatedAt(System.currentTimeMillis() - 60_000);
		stub.setServerUpdatedAt(System.currentTimeMillis() - 60_000);
		restaurantProfileRepository.saveAndFlush(stub);

		// Give the pushed row the business data and the higher id, like the real push did.
		RestaurantProfile data = restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow();
		data.setLocalId(restaurantId);
		data.setShopName("Saket Cafe");
		data.setCurrency("INR");
		data.setWhatsappNumber("9867545806");
		data.setServerUpdatedAt(System.currentTimeMillis());
		restaurantProfileRepository.saveAndFlush(data);
	}

	private void dropSingletonConstraint() {
		jdbcTemplate.execute("ALTER TABLE restaurantprofiles DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
	}

	/** Re-applies the invariant so later tests in this shared H2 database keep it. */
	private void singletonConstraintPresent() {
		jdbcTemplate.execute("ALTER TABLE restaurantprofiles ADD CONSTRAINT IF NOT EXISTS "
				+ CONSTRAINT + " UNIQUE (restaurant_id)");
	}

	private String profilePushJson(Long restaurantId, Long localId, String shopName, String currency, String whatsapp) {
		long now = System.currentTimeMillis();
		return """
				[{
				  "localId": %d,
				  "deviceId": "DEV_PROFILE",
				  "restaurantId": %d,
				  "createdAt": %d,
				  "updatedAt": %d,
				  "isDeleted": false,
				  "shopName": "%s",
				  "currency": "%s",
				  "whatsappNumber": "%s"
				}]
				""".formatted(localId, restaurantId, now, now, shopName, currency, whatsapp);
	}
}