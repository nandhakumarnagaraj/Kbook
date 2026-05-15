package com.khanabook.saas.repository;

import com.khanabook.saas.entity.EasebuzzSubMerchantWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EasebuzzSubMerchantWebhookEventRepository extends JpaRepository<EasebuzzSubMerchantWebhookEvent, Long> {

    List<EasebuzzSubMerchantWebhookEvent> findBySubMerchantIdOrderByReceivedAtDesc(String subMerchantId);

    List<EasebuzzSubMerchantWebhookEvent> findByProcessedFalse();
}
