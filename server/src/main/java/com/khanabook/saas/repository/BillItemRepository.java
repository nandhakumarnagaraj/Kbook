package com.khanabook.saas.repository;

import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillItemRepository extends SyncRepository<BillItem, Long> {

	@Query("SELECT bi.menuItemId, MAX(bi.itemName), SUM(bi.quantity), SUM(bi.itemTotal) " +
			"FROM BillItem bi WHERE bi.restaurantId = :restaurantId AND bi.isDeleted = false " +
			"AND bi.createdAt >= :from AND bi.createdAt < :to AND bi.menuItemId IS NOT NULL " +
			"GROUP BY bi.menuItemId")
	List<Object[]> aggregateSalesByMenuItem(@Param("restaurantId") Long restaurantId,
											@Param("from") Long from, @Param("to") Long to);

	List<BillItem> findByServerBillIdAndIsDeletedFalseOrderById(Long serverBillId);

	List<BillItem> findByRestaurantIdAndCreatedAtBetween(Long restaurantId, Long from, Long to);

	List<BillItem> findByRestaurantIdAndServerBillIdIn(Long restaurantId, List<Long> serverBillIds);

	List<BillItem> findByRestaurantIdAndServerBillIdInAndServerUpdatedAtGreaterThan(Long restaurantId, List<Long> serverBillIds, Long serverUpdatedAt);

	@Query("""
			SELECT i FROM BillItem i
			JOIN Bill b ON i.serverBillId = b.id
			WHERE i.restaurantId = :restaurantId
			  AND i.serverUpdatedAt > :lastSyncTimestamp
			  AND (
					b.createdTerminalId = :terminalId
					OR b.currentOwnerTerminalId = :terminalId
				  )
			""")
	List<BillItem> findUpdatedForTerminal(
			@Param("restaurantId") Long restaurantId,
			@Param("lastSyncTimestamp") Long lastSyncTimestamp,
			@Param("terminalId") String terminalId);
}
