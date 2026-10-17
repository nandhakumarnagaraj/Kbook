package com.khanabook.saas.feature.payments.data;

import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EasebuzzSubMerchantWebhookEventRepository extends JpaRepository<EasebuzzSubMerchantWebhookEvent, Long> {

    List<EasebuzzSubMerchantWebhookEvent> findBySubMerchantIdOrderByReceivedAtDesc(String subMerchantId);

    List<EasebuzzSubMerchantWebhookEvent> findByProcessedFalse();
}
