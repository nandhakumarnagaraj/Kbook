package com.khanabook.saas.repository;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RestaurantProfileRepository extends SyncRepository<RestaurantProfile, Long> {
	Optional<RestaurantProfile> findByRestaurantId(Long restaurantId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query(value = """
			UPDATE restaurantprofiles
			SET
			  daily_order_counter = CASE
			    WHEN last_reset_date = :today THEN COALESCE(daily_order_counter, 0) + 1
			    ELSE COALESCE(daily_order_counter, 0) + 1
			  END,
			  lifetime_order_counter = COALESCE(lifetime_order_counter, 0) + 1,
			  last_reset_date = :today,
			  updated_at = :now,
			  server_updated_at = :now
			WHERE restaurant_id = :restaurantId
			""", nativeQuery = true)
	int incrementCountersAtomic(Long restaurantId, String today, Long now);

	@org.springframework.data.jpa.repository.Query(value = "SELECT daily_order_counter, lifetime_order_counter FROM restaurantprofiles WHERE restaurant_id = :restaurantId", nativeQuery = true)
	java.util.List<Object[]> getCounters(Long restaurantId);
}
