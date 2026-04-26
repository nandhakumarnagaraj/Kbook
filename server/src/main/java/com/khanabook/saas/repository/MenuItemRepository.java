package com.khanabook.saas.repository;

import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends SyncRepository<MenuItem, Long> {

	@Query(
			value = """
					SELECT *
					FROM menuitems
					WHERE restaurant_id = :restaurantId
					  AND category_id = :categoryId
					  AND is_deleted = false
					  AND lower(regexp_replace(btrim(name), '\\s+', ' ', 'g')) = :normalizedName
					LIMIT 1
					""",
			nativeQuery = true)
	Optional<MenuItem> findActiveDuplicateByNormalizedName(
			@Param("restaurantId") Long restaurantId,
			@Param("categoryId") Long categoryId,
			@Param("normalizedName") String normalizedName);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE MenuItem m SET m.currentStock = (SELECT COALESCE(SUM(s.delta), 0) FROM StockLog s WHERE s.serverMenuItemId = :id AND s.isDeleted = false) WHERE m.id = :id")
	void recalculateStock(@Param("id") Long id);

	long countByRestaurantIdAndIsDeletedFalse(Long restaurantId);

	@Query("SELECT m.restaurantId, COUNT(m) FROM MenuItem m WHERE m.isDeleted = false GROUP BY m.restaurantId")
	java.util.List<Object[]> countGroupedByRestaurant();
}
