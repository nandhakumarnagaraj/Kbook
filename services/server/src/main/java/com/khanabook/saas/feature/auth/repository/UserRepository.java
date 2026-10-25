package com.khanabook.saas.feature.auth.repository;

import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.sync.data.SyncRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends SyncRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByEmailIgnoreCase(String email);

	Optional<User> findByLoginId(String loginId);

	Optional<User> findByLoginIdIgnoreCase(String loginId);

	Optional<User> findByWhatsappNumber(String whatsappNumber);

	Optional<User> findByGoogleEmail(String googleEmail);

	Optional<User> findByGoogleEmailIgnoreCase(String googleEmail);

	Optional<User> findByPhoneNumber(String phoneNumber);

	@Query("SELECT u FROM User u WHERE u.phoneNumber = :identifier OR u.loginId = :identifier OR u.email = :identifier OR u.whatsappNumber = :identifier")
	Optional<User> findByAnyIdentifier(@org.springframework.data.repository.query.Param("identifier") String identifier);

	/**
	 * Signup availability check: a number is only "taken" if it belongs to a
	 * live (not soft-deleted) user. Soft-deleted accounts must not block
	 * re-registration of the same number. See AuthServiceImpl#ensurePhoneNumberAvailableForSignup.
	 */
	@Query("SELECT u FROM User u WHERE u.isDeleted = false AND (u.phoneNumber = :identifier OR u.loginId = :identifier OR u.email = :identifier OR u.whatsappNumber = :identifier)")
	Optional<User> findActiveByAnyIdentifier(@org.springframework.data.repository.query.Param("identifier") String identifier);

	/**
	 * Soft-deleted users that still hold this identifier in a partial-unique-indexed
	 * column (phone_number / login_id / whatsapp_number). Used to release the
	 * identifier at signup time so the DB unique index does not block reuse.
	 */
	@Query("SELECT u FROM User u WHERE u.isDeleted = true AND (u.phoneNumber = :identifier OR u.loginId = :identifier OR u.whatsappNumber = :identifier)")
	List<User> findDeletedHoldingIdentifier(@org.springframework.data.repository.query.Param("identifier") String identifier);

	List<User> findByRestaurantIdAndRoleAndIsDeletedFalse(Long restaurantId, UserRole role);

	List<User> findByRestaurantIdAndIsDeletedFalse(Long restaurantId);

	long countByIsDeletedFalse();

	// Per-restaurant staff counts for admin businesses list
	@Query("SELECT u.restaurantId, COUNT(u) FROM User u WHERE u.isDeleted = false GROUP BY u.restaurantId")
	List<Object[]> countStaffGroupedByRestaurant();

	// Per-restaurant owner for admin businesses list
	@Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.role = com.khanabook.saas.feature.auth.entity.UserRole.OWNER")
	List<User> findAllActiveOwners();

	/**
	 * Presence heartbeat (V118). Native on purpose: {@code last_seen_at} is not
	 * mapped on {@link User}, because users travel in the master-sync payload as
	 * UserDTO and a presence timestamp must not enter that contract.
	 *
	 * <p>The {@code cutoff} is the throttle: the row is written at most once a
	 * minute per user, so a tab left open costs one UPDATE a minute and not one per
	 * request. {@code REQUIRES_NEW} keeps the write out of the caller's transaction,
	 * which may be read-only — an UPDATE attempted in a read-only transaction poisons
	 * every later query in the same request.
	 */
	@Modifying
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Query(value = "UPDATE users SET last_seen_at = :now "
			+ "WHERE id = :userId AND (last_seen_at IS NULL OR last_seen_at < :cutoff)",
			nativeQuery = true)
	int touchLastSeen(@org.springframework.data.repository.query.Param("userId") Long userId,
			@org.springframework.data.repository.query.Param("now") Long now,
			@org.springframework.data.repository.query.Param("cutoff") Long cutoff);

	boolean existsByEmail(String email);

	boolean existsByLoginId(String loginId);

	boolean existsByWhatsappNumber(String whatsappNumber);

	boolean existsByPhoneNumber(String phoneNumber);
}
