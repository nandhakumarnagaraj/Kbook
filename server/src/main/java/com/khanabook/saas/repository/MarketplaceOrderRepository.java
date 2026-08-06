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

    /**
     * Idempotency key for the webhook upsert: the platform + external order id
     * pair is unique (unique constraint uk_marketplace_order_platform_order_id
     * from V21), so a retry from the provider resolves to an existing row.
     */
    Optional<MarketplaceOrder> findByPlatformAndPlatformOrderId(String platform, String platformOrderId);
}
