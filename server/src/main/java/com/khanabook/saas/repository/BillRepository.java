package com.khanabook.saas.repository;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BillRepository extends SyncRepository<Bill, Long> {

    // Returns bills updated since lastSync, excluding own-device bills UNLESS they are deleted.
    // This ensures server-side soft-deletes (e.g. dedup cleanup) propagate back to the device.
    @Query("SELECT b FROM Bill b WHERE b.restaurantId = :restaurantId " +
           "AND b.serverUpdatedAt > :lastSyncTimestamp " +
           "AND (b.deviceId != :deviceId OR b.isDeleted = true)")
    List<Bill> findUpdatedExcludingOwnActiveOnly(
            @Param("restaurantId") Long restaurantId,
            @Param("lastSyncTimestamp") Long lastSyncTimestamp,
            @Param("deviceId") String deviceId);
}
