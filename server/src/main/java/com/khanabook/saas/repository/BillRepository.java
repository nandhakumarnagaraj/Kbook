package com.khanabook.saas.repository;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillRepository extends SyncRepository<Bill, Long> {

}
