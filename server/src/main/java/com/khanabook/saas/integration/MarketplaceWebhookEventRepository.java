package com.khanabook.saas.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketplaceWebhookEventRepository extends JpaRepository<MarketplaceWebhookEvent, Long> {
    List<MarketplaceWebhookEvent> findTop20ByRestaurantIdOrderByReceivedAtDesc(Long restaurantId);
    List<MarketplaceWebhookEvent> findTop200ByOrderByReceivedAtDesc();
}
