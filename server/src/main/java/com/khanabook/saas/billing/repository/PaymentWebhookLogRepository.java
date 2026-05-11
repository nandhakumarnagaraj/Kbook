package com.khanabook.saas.billing.repository;

import com.khanabook.saas.billing.entity.PaymentWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, Long> {
}
