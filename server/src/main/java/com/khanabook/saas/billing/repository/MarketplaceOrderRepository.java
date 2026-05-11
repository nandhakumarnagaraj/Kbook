package com.khanabook.saas.billing.repository;

import com.khanabook.saas.billing.domain.MarketplaceOrder;
import com.khanabook.saas.billing.domain.MarketplaceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, Long> {
    Optional<MarketplaceOrder> findByPlatformAndPlatformOrderId(String platform, String platformOrderId);
    Optional<MarketplaceOrder> findByRestaurantIdAndPlatformOrderId(Long restaurantId, String platformOrderId);
    Optional<MarketplaceOrder> findByBillId(Long billId);
    List<MarketplaceOrder> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<MarketplaceOrder> findByRestaurantIdAndPlatformOrderByCreatedAtDesc(Long restaurantId, String platform);

    @Query("SELECT i FROM MarketplaceOrderItem i WHERE i.marketplaceOrderId = :orderId")
    List<MarketplaceOrderItem> findMarketplaceOrderItems(@Param("orderId") Long orderId);
}
