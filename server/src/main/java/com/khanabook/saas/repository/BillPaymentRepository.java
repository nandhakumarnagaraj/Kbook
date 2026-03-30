package com.khanabook.saas.repository;

import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillPaymentRepository extends SyncRepository<BillPayment, Long> {

}
