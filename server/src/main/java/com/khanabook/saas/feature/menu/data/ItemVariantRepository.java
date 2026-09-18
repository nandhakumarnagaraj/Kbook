package com.khanabook.saas.feature.menu.data;

import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.sync.data.SyncRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemVariantRepository extends SyncRepository<ItemVariant, Long> {
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE ItemVariant v SET v.currentStock = (SELECT COALESCE(SUM(s.delta), 0) FROM StockLog s WHERE s.serverVariantId = :id AND s.isDeleted = false) WHERE v.id = :id")
	void recalculateStock(@Param("id") Long id);

	long countByMenuItemIdAndIsDeletedFalse(Long menuItemId);

	java.util.List<ItemVariant> findByServerMenuItemIdAndIsDeletedFalse(Long serverMenuItemId);
}
