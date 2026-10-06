package com.khanabook.saas.feature.restaurants.data;

import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.sync.data.SyncRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantProfileRepository extends SyncRepository<RestaurantProfile, Long> {

	/**
	 * The one canonical profile row for a restaurant, newest row first.
	 *
	 * <p>A restaurant holds exactly ONE profile row — {@code restaurant_id} is its
	 * identity. The table's only unique key used to be the generic
	 * {@code (restaurant_id, device_id, local_id)} composite, which does not enforce
	 * that: the server writes the signup stub under {@code local_id = 1} while the app
	 * pushes its profile keyed on {@code local_id = restaurant_id}, so a second row
	 * could satisfy the composite key and be inserted (V107 now blocks that at the
	 * database level, and {@code GenericSyncService} resolves profile pushes by
	 * {@code restaurant_id}).
	 *
	 * <p>Derived + {@code findFirst} so the query is capped at one row. A plain
	 * {@code Optional<RestaurantProfile> findByRestaurantId} has no such cap: with two
	 * rows Spring Data throws
	 * {@code IncorrectResultSizeDataAccessException} ("Query did not return a unique
	 * result: 2 results were returned"), which surfaced as an HTTP 500 on every
	 * single-row reader — login, the JWT filter, invoices, tax compliance, terminal
	 * approval.
	 *
	 * <p>Ties are broken by {@code id DESC} so the winner is deterministic. The
	 * newest row is the app-pushed one: signup always inserts the bare stub first
	 * (no currency, no whatsapp number), and the first device push lands a second,
	 * higher-id row carrying the real business data seconds later. Picking the oldest
	 * row would hand those callers an empty profile, which is worse than the crash.
	 */
	Optional<RestaurantProfile> findFirstByRestaurantIdOrderByIdDesc(Long restaurantId);

	/**
	 * Single-row reader for the one profile row of a restaurant.
	 *
	 * <p>Never throws on a legacy duplicate row — see
	 * {@link #findFirstByRestaurantIdOrderByIdDesc} for why a duplicate can still
	 * exist and which row wins.
	 */
	default Optional<RestaurantProfile> findByRestaurantId(Long restaurantId) {
		if (restaurantId == null) {
			return Optional.empty();
		}
		return findFirstByRestaurantIdOrderByIdDesc(restaurantId);
	}

	/**
	 * Pessimistic write-lock on the restaurant's profile row. Used as a stable parent-row
	 * lock for atomic terminal-count enforcement (5-terminal limit). Two concurrent
	 * approval transactions both lock this row, serializing the count check.
	 *
	 * <p>Resolves the canonical row first and then locks it BY PRIMARY KEY rather than
	 * locking a {@code WHERE restaurant_id = ?} result set. Both matter:
	 * <ul>
	 *   <li>one row, so the lock is never spread over two rows of the same restaurant
	 *       (the legacy duplicate case made a {@code findOne}-style lock query throw
	 *       {@code IncorrectResultSizeDataAccessException}, failing terminal approval
	 *       with a 500 inside a transaction that must stay atomic);</li>
	 *   <li>by primary key, so every caller locks the same physical row — a
	 *       secondary-index predicate lock can be skipped by the planner under
	 *       {@code INCLUDE}-style index-only reads, whereas a PK lock always waits.</li>
	 * </ul>
	 */
	default Optional<RestaurantProfile> findAndLockByRestaurantId(Long restaurantId) {
		return findByRestaurantId(restaurantId)
				.flatMap(profile -> findByIdWithLock(profile.getId()));
	}

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM RestaurantProfile p WHERE p.id = :id")
	Optional<RestaurantProfile> findByIdWithLock(
			@org.springframework.data.repository.query.Param("id") Long id);

	/**
	 * Fail-closed suspension check across every row of a restaurant.
	 *
	 * <p>Deliberately NOT a read of the single canonical row: with two profile rows for
	 * one restaurant, deciding suspension from whichever row the reader happened to get
	 * would let a suspended business back in through the other row (suspension is
	 * enforced by this state on login and on every authenticated request). Any row
	 * marked suspended blocks the restaurant.
	 */
	boolean existsByRestaurantIdAndIsSuspendedTrue(Long restaurantId);

	/**
	 * All profile rows of a restaurant, oldest first. Only meaningful while a legacy
	 * duplicate can still exist — once V107's unique index is in place this is always
	 * the single canonical row. Used by server-side state changes that must not depend
	 * on which duplicate happened to be read.
	 */
	List<RestaurantProfile> findAllByRestaurantIdOrderByIdAsc(Long restaurantId);

	long countByIsDeletedFalse();

	long countByIsDeletedFalseAndOwnWebsiteEnabledTrue();

	List<RestaurantProfile> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

	// Atomic counter increment: resets daily_order_counter when the date rolls over.
	@org.springframework.data.jpa.repository.Modifying
	@Query(value = """
			UPDATE restaurantprofiles
			SET
			  daily_order_counter = CASE
			    WHEN last_reset_date_proper < CAST(:today AS DATE) OR last_reset_date_proper IS NULL
			    THEN 1
			    ELSE COALESCE(daily_order_counter, 0) + 1
			  END,
			  lifetime_order_counter = COALESCE(lifetime_order_counter, 0) + 1,
			  last_reset_date        = :today,
			  last_reset_date_proper = CAST(:today AS DATE),
			  updated_at             = :now,
			  server_updated_at      = :now
			WHERE restaurant_id = :restaurantId
			""", nativeQuery = true)
	int incrementCountersAtomic(Long restaurantId, String today, Long now);

	@Query(value = "SELECT daily_order_counter, lifetime_order_counter FROM restaurantprofiles WHERE restaurant_id = :restaurantId", nativeQuery = true)
	java.util.List<Object[]> getCounters(Long restaurantId);
}
