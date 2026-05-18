package com.khanabook.saas.repository;

import com.khanabook.saas.entity.MarketplaceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, Long> {

    List<MarketplaceOrder> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    List<MarketplaceOrder> findByRestaurantIdAndOrderStatusOrderByCreatedAtDesc(Long restaurantId, String orderStatus);

    List<MarketplaceOrder> findByRestaurantIdAndOrderStatusInOrderByCreatedAtDesc(Long restaurantId, List<String> statuses);

    long countByRestaurantIdAndOrderStatus(Long restaurantId, String orderStatus);

    Optional<MarketplaceOrder> findByPlatformAndPlatformOrderId(String platform, String platformOrderId);
}
