package com.khanabook.saas.identity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Identity uniqueness against a real PostgreSQL instance, with the real migrations.
 *
 * <p>WHY: the identity rules that keep duplicate accounts out live in the database — partial
 * unique indexes created by V4 and V105 — but the existing identity tests are Mockito-based and
 * never touch them. A check-then-insert in {@code AuthServiceImpl} is only safe because of those
 * indexes, so the safety net itself has to be tested, including under concurrency.
 *
 * <p>WHAT: applies every migration to a throwaway container, then asserts:
 * <ul>
 *   <li>a second account cannot take an existing phone number, in any format variant;</li>
 *   <li>concurrent inserts of the same number leave exactly one row;</li>
 *   <li>a soft-deleted account can release its number for reuse.</li>
 * </ul>
 *
 * <p>HOW: no Spring context. Flyway builds the schema, plain JDBC exercises the constraints, so the
 * test fails for schema reasons only and cannot be confused by application wiring.
 *
 * <p>LIMITATIONS: does not exercise the HTTP layer or {@code AuthServiceImpl} ordering. It pins the
 * database guarantees, which is the layer that cannot be bypassed by a code change.
 */
@Testcontainers(disabledWithoutDocker = true)
class PhoneIdentityIntegrityTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:16-alpine");

	/** Fictional sample number: no real subscriber is implied. */
	private static final String PHONE = "9812345670";

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration")
				.load()
				.migrate();
	}

	@BeforeEach
	void clean() throws SQLException {
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.execute("DELETE FROM users");
			s.execute("DELETE FROM restaurantprofiles");
		}
	}

	// ── uniqueness, sequential ───────────────────────────────────────────────

	@Test
	void secondAccountWithSamePhone_isRejectedByTheDatabase() throws SQLException {
		insertUser(1, PHONE, PHONE);
		assertThat(countUsers()).isEqualTo(1);

		assertThatThrownBy(() -> insertUser(2, PHONE, PHONE))
				.isInstanceOf(SQLException.class);
		assertThat(countUsers()).isEqualTo(1);
	}

	@Test
	void phoneFormatVariants_collideOnTheDigitsIndex() throws SQLException {
		insertUser(1, PHONE, PHONE);

		// Same subscriber, different spelling. ux_users_phone_digits (V105) extracts the
		// trailing ten digits, so both must be rejected even though the raw strings differ.
		assertThatThrownBy(() -> insertUser(2, "+91" + PHONE, "+91" + PHONE))
				.isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> insertUser(3, "0" + PHONE, "0" + PHONE))
				.isInstanceOf(SQLException.class);

		assertThat(countUsers()).isEqualTo(1);
	}

	// ── uniqueness, concurrent ───────────────────────────────────────────────

	@Test
	void concurrentSignupsWithSamePhone_leaveExactlyOneRow() throws Exception {
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicInteger inserted = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		for (int i = 0; i < threads; i++) {
			final long localId = i + 1;
			pool.submit(() -> {
				try {
					start.await();
					insertUser(localId, PHONE, PHONE);
					inserted.incrementAndGet();
				} catch (SQLException e) {
					rejected.incrementAndGet();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
		}

		start.countDown();
		assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
		pool.shutdown();

		// This is what makes the check-then-insert in AuthServiceImpl safe: the unique
		// index decides, not the pre-check, so a race cannot mint a duplicate account.
		assertThat(inserted.get()).isEqualTo(1);
		assertThat(rejected.get()).isEqualTo(threads - 1);
		assertThat(countUsers()).isEqualTo(1);
	}

	// ── soft-delete reuse ────────────────────────────────────────────────────

	@Test
	void softDeletedAccount_canReleaseItsNumber() throws SQLException {
		insertUser(1, PHONE, PHONE);

		// AuthServiceImpl.releaseIdentifierFromDeletedUsers() performs exactly this, then signs up.
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.executeUpdate("UPDATE users SET is_deleted = true, phone_number = NULL, "
					+ "whatsapp_number = NULL, login_id = login_id || '|deleted:' || id WHERE local_id = 1");
		}

		insertUser(2, PHONE, PHONE);
		assertThat(countUsers()).isEqualTo(2);
	}

	// ── known defect, pinned ─────────────────────────────────────────────────

	@Test
	@Disabled("RC-2 / decision D-4: enable when `email` gets case-insensitive uniqueness. "
			+ "Today two rows may hold 'A@x.com' and 'a@x.com' while every lookup is "
			+ "case-insensitive, which makes login throw IncorrectResultSizeDataAccessException (HTTP 500).")
	void caseVariantEmails_areRejected() throws SQLException {
		insertUserWithEmail(1, "August@Example.com");
		assertThatThrownBy(() -> insertUserWithEmail(2, "august@example.com"))
				.isInstanceOf(SQLException.class);
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private static Connection connect() throws SQLException {
		return DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private static void insertUser(long localId, String loginId, String phone) throws SQLException {
		insert(localId, loginId, phone, null);
	}

	private static void insertUserWithEmail(long localId, String email) throws SQLException {
		insert(localId, "login" + localId, null, email);
	}

	private static void insert(long localId, String loginId, String phone, String email)
			throws SQLException {
		String sql = "INSERT INTO users (local_id, device_id, restaurant_id, updated_at, "
				+ "server_updated_at, created_at, name, login_id, phone_number, whatsapp_number, "
				+ "email, auth_provider, role, is_active, version) "
				+ "VALUES (" + localId + ", 'test-device', " + (900000L + localId) + ", "
				+ "1763000000000, 1763000000000, 1763000000000, 'Test Owner', "
				+ q(loginId) + ", " + q(phone) + ", " + q(phone) + ", " + q(email) + ", "
				+ "'PHONE', 'OWNER', true, 0)";
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.executeUpdate(sql);
		}
	}

	private static String q(String v) {
		return v == null ? "NULL" : "'" + v.replace("'", "''") + "'";
	}

	private static int countUsers() throws SQLException {
		try (Connection c = connect();
				Statement s = c.createStatement();
				var rs = s.executeQuery("SELECT count(*) FROM users")) {
			rs.next();
			return rs.getInt(1);
		}
	}
}
