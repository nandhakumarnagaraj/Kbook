package com.khanabook.saas.repository;

import com.khanabook.saas.entity.EasebuzzWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EasebuzzWebhookEventRepository extends JpaRepository<EasebuzzWebhookEvent, Long> {
    Optional<EasebuzzWebhookEvent> findByRestaurantIdAndTxnId(Long restaurantId, String txnId);
    Optional<EasebuzzWebhookEvent> findFirstByTxnIdOrderByReceivedAtDesc(String txnId);
}
