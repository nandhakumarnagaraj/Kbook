package com.khanabook.saas.repository;

import com.khanabook.saas.entity.StockLog;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockLogRepository extends SyncRepository<StockLog, Long> {

}
