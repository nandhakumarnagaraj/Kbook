package com.khanabook.saas.billing.repository;

import com.khanabook.saas.billing.entity.EasebuzzSubMerchantWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EasebuzzSubMerchantWebhookEventRepository extends JpaRepository<EasebuzzSubMerchantWebhookEvent, Long> {
    List<EasebuzzSubMerchantWebhookEvent> findTop20BySubMerchantIdOrderByReceivedAtDesc(String subMerchantId);
    List<EasebuzzSubMerchantWebhookEvent> findTop200ByOrderByReceivedAtDesc();
}
