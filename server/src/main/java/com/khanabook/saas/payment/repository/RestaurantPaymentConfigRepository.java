package com.khanabook.saas.payment.repository;

import com.khanabook.saas.payment.entity.PaymentGateway;
import com.khanabook.saas.payment.entity.RestaurantPaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantPaymentConfigRepository extends JpaRepository<RestaurantPaymentConfig, Long> {
    Optional<RestaurantPaymentConfig> findByRestaurantIdAndGatewayName(Long restaurantId, PaymentGateway gatewayName);
    Optional<RestaurantPaymentConfig> findByMerchantKeyAndGatewayName(String merchantKey, PaymentGateway gatewayName);
}
