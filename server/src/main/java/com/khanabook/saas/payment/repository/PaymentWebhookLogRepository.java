package com.khanabook.saas.payment.repository;

import com.khanabook.saas.payment.entity.PaymentWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, Long> {
}
