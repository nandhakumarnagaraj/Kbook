package com.khanabook.saas.feature.inventory.data;

import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.sync.data.SyncRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockLogRepository extends SyncRepository<StockLog, Long> {

}
