package com.khanabook.saas.payment.repository;

import com.khanabook.saas.payment.entity.Payment;
import com.khanabook.saas.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRestaurantIdAndGatewayTxnId(Long restaurantId, String gatewayTxnId);
    Optional<Payment> findTopByRestaurantIdAndBillIdOrderByCreatedAtDesc(Long restaurantId, Long billId);
    boolean existsByRestaurantIdAndBillIdAndPaymentStatus(Long restaurantId, Long billId, PaymentStatus paymentStatus);
    List<Payment> findByRestaurantIdAndBillIdOrderByCreatedAtDesc(Long restaurantId, Long billId);
}
