package com.khanabook.saas.feature.payments.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface RefundAttemptRepository extends JpaRepository<RefundAttempt, Long> {
    Optional<RefundAttempt> findByBillIdAndGatewayRefundId(Long billId, String gatewayRefundId);

    @Query("select coalesce(sum(a.amount), 0) from RefundAttempt a where a.billId = :billId and a.status = :status")
    BigDecimal sumAmountByBillIdAndStatus(@Param("billId") Long billId, @Param("status") String status);
}
