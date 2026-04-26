package com.khanabook.saas.repository;

import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
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

	List<User> findByRestaurantIdAndRoleAndIsDeletedFalse(Long restaurantId, UserRole role);

	List<User> findByRestaurantIdAndIsDeletedFalse(Long restaurantId);

	long countByIsDeletedFalse();

	// Per-restaurant staff counts for admin businesses list
	@Query("SELECT u.restaurantId, COUNT(u) FROM User u WHERE u.isDeleted = false GROUP BY u.restaurantId")
	List<Object[]> countStaffGroupedByRestaurant();

	// Per-restaurant owner for admin businesses list
	@Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.role = com.khanabook.saas.entity.UserRole.OWNER")
	List<User> findAllActiveOwners();

	boolean existsByEmail(String email);

	boolean existsByLoginId(String loginId);

	boolean existsByWhatsappNumber(String whatsappNumber);

	boolean existsByPhoneNumber(String phoneNumber);
}
