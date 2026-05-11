package com.khanabook.saas.billing.repository;

import com.khanabook.saas.billing.domain.BillPayment;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillPaymentRepository extends SyncRepository<BillPayment, Long> {
    boolean existsByRestaurantIdAndGatewayTxnId(Long restaurantId, String gatewayTxnId);
}
