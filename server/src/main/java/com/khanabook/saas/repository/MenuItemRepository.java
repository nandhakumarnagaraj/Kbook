package com.khanabook.saas.repository;

import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuItemRepository extends SyncRepository<MenuItem, Long> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE MenuItem m SET m.currentStock = (SELECT COALESCE(SUM(s.delta), 0) FROM StockLog s WHERE s.serverMenuItemId = :id AND s.isDeleted = false) WHERE m.id = :id")
	void recalculateStock(@Param("id") Long id);
}
