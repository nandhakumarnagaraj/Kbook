package com.khanabook.saas.billing.repository;

import com.khanabook.saas.billing.domain.Payment;
import com.khanabook.saas.billing.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findTopByGatewayTxnIdOrderByCreatedAtDesc(String gatewayTxnId);
    Optional<Payment> findByRestaurantIdAndGatewayTxnId(Long restaurantId, String gatewayTxnId);
    Optional<Payment> findTopByRestaurantIdAndBillIdOrderByCreatedAtDesc(Long restaurantId, Long billId);
    boolean existsByRestaurantIdAndBillIdAndPaymentStatus(Long restaurantId, Long billId, PaymentStatus paymentStatus);
    List<Payment> findByRestaurantIdAndBillIdOrderByCreatedAtDesc(Long restaurantId, Long billId);
    List<Payment> findByRestaurantIdAndBillIdIn(Long restaurantId, Collection<Long> billIds);
    Optional<Payment> findTopByRefundGatewayRefundIdOrderByUpdatedAtDesc(String refundGatewayRefundId);
    Optional<Payment> findTopByMerchantRefundIdOrderByUpdatedAtDesc(String merchantRefundId);
    Optional<Payment> findTopByGatewayPaymentIdOrderByUpdatedAtDesc(String gatewayPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.gatewayTxnId = :gatewayTxnId ORDER BY p.createdAt DESC")
    Optional<Payment> findTopByGatewayTxnIdOrderByCreatedAtDescForUpdate(@Param("gatewayTxnId") String gatewayTxnId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.restaurantId = :restaurantId AND p.gatewayTxnId = :gatewayTxnId")
    Optional<Payment> findByRestaurantIdAndGatewayTxnIdForUpdate(@Param("restaurantId") Long restaurantId, @Param("gatewayTxnId") String gatewayTxnId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.restaurantId = :restaurantId AND p.billId = :billId ORDER BY p.createdAt DESC")
    Optional<Payment> findTopByRestaurantIdAndBillIdOrderByCreatedAtDescForUpdate(@Param("restaurantId") Long restaurantId, @Param("billId") Long billId);
}
