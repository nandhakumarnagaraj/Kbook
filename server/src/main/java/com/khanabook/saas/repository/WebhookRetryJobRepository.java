package com.khanabook.saas.repository;

import com.khanabook.saas.entity.WebhookRetryJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookRetryJobRepository extends JpaRepository<WebhookRetryJob, Long> {

    List<WebhookRetryJob> findByStatusAndNextAttemptAtLessThanEqual(String status, long nextAttemptAt);

    @Query("SELECT w.status, COUNT(w) FROM WebhookRetryJob w GROUP BY w.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT w.webhookType, w.status, COUNT(w) FROM WebhookRetryJob w GROUP BY w.webhookType, w.status")
    List<Object[]> countByTypeAndStatusGrouped();

    long countByStatus(String status);

    List<WebhookRetryJob> findByStatusOrderByCreatedAtDesc(String status);
}