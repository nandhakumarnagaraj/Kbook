package com.khanabook.saas.feature.notifications.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    Optional<EmailOutbox> findByReportTypeAndReferenceKeyAndRevisionAndRecipient(
            String reportType, String referenceKey, Integer revision, String recipient);

    boolean existsByReportTypeAndReferenceKeyAndRecipientAndStatus(
            String reportType, String referenceKey, String recipient, String status);

    boolean existsByRestaurantIdAndReportTypeAndReferenceKeyAndStatus(
            Long restaurantId, String reportType, String referenceKey, String status);

    @Query("SELECT e FROM EmailOutbox e WHERE e.status IN :statuses AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now) ORDER BY e.nextAttemptAt ASC")
    List<EmailOutbox> findPendingEmails(
            @Param("statuses") Collection<String> statuses,
            @Param("now") Long now);

    List<EmailOutbox> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
}
