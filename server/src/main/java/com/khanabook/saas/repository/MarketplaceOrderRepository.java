package com.khanabook.saas.repository;

import com.khanabook.saas.entity.MarketplaceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, Long> {

    List<MarketplaceOrder> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    Optional<MarketplaceOrder> findByPlatformAndPlatformOrderId(String platform, String platformOrderId);
}
