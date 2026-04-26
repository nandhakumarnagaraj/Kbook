package com.khanabook.saas.storefront.repository;

import com.khanabook.saas.storefront.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByTrackingToken(String trackingToken);
    Optional<CustomerOrder> findByRestaurantIdAndId(Long restaurantId, Long id);
    List<CustomerOrder> findByRestaurantIdOrderByCreatedAtDescIdDesc(Long restaurantId);
    boolean existsByPublicOrderCode(String publicOrderCode);
}
