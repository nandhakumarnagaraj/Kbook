package com.khanabook.saas.feature.menu.data;

import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.sync.data.SyncRepository;
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

	java.util.List<MenuItem> findByRestaurantIdAndIsDeletedFalse(Long restaurantId);

	boolean existsByRestaurantIdAndCategoryIdAndIsDeletedFalse(Long restaurantId, Long categoryId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE MenuItem m SET m.isAvailable = false, m.updatedAt = :updatedAt WHERE m.id = :id AND m.restaurantId = :restaurantId")
	int markAsUnavailable(@Param("id") Long id, @Param("restaurantId") Long restaurantId, @Param("updatedAt") Long updatedAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE MenuItem m SET m.isAvailable = true, m.updatedAt = :updatedAt WHERE m.id = :id AND m.restaurantId = :restaurantId AND m.isDeleted = false")
	int markAsAvailable(@Param("id") Long id, @Param("restaurantId") Long restaurantId, @Param("updatedAt") Long updatedAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE MenuItem m SET m.isAvailable = false, m.updatedAt = :updatedAt WHERE m.restaurantId = :restaurantId AND m.isAvailable = true AND m.isDeleted = false")
	int markAllAsUnavailable(@Param("restaurantId") Long restaurantId, @Param("updatedAt") Long updatedAt);

	/**
	 * Recomputes {@code menuitems.has_variants} from the live variant rows instead of
	 * trusting a client to declare it.
	 *
	 * <p>Nothing on this server creates or deletes variants on its own initiative: the
	 * terminal is the only place variant CRUD happens, and menu items and variants are
	 * pushed as two separate requests. So a client-declared flag is either absent (every
	 * build released before the field existed) or racing the variant rows that justify it.
	 * Deriving the value here keeps a single invariant true:
	 * {@code has_variants == EXISTS(live variants)}.
	 *
	 * <p>Written as native SQL because a subquery in a JPQL bulk-update SET clause is
	 * only validated at execution time, so a mistake there would surface in production
	 * rather than at build time.
	 *
	 * <p>{@code server_updated_at} is advanced deliberately. The item pull cursor is
	 * {@code server_updated_at}, so a correction that did not advance it would never
	 * reach the other terminals - which is the exact symptom this exists to repair. No
	 * other column is touched, so recomputing can never roll back an edit the user just
	 * made.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE menuitems m
			   SET has_variants = EXISTS (
			         SELECT 1 FROM itemvariants v
			          WHERE v.server_menu_item_id = m.id AND v.is_deleted = false),
			       server_updated_at = :serverUpdatedAt
			 WHERE m.restaurant_id = :restaurantId
			   AND m.id IN (:ids)
			""", nativeQuery = true)
	int recomputeHasVariantsFlag(@Param("ids") java.util.Collection<Long> ids,
			@Param("restaurantId") Long restaurantId,
			@Param("serverUpdatedAt") Long serverUpdatedAt);
}
