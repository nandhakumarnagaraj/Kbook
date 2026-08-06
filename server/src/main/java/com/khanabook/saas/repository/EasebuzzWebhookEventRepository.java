package com.khanabook.saas.repository;

import com.khanabook.saas.entity.EasebuzzWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface EasebuzzWebhookEventRepository extends JpaRepository<EasebuzzWebhookEvent, Long>,
        JpaSpecificationExecutor<EasebuzzWebhookEvent> {

    Optional<EasebuzzWebhookEvent> findByRestaurantIdAndTxnId(Long restaurantId, String txnId);

    long countByReceivedAtBetweenAndStatus(Long from, Long to, String status);

    long countByReceivedAtBetween(Long from, Long to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EasebuzzWebhookEvent e WHERE e.receivedAt BETWEEN :from AND :to AND e.status = :status")
    BigDecimal sumAmountByReceivedAtBetweenAndStatus(@Param("from") Long from, @Param("to") Long to, @Param("status") String status);

    @Query("SELECT e.restaurantId, COUNT(e) FROM EasebuzzWebhookEvent e WHERE e.receivedAt BETWEEN :from AND :to GROUP BY e.restaurantId")
    List<Object[]> countGroupedByRestaurantIdBetween(@Param("from") Long from, @Param("to") Long to);

    @Query("SELECT e.status, COUNT(e) FROM EasebuzzWebhookEvent e WHERE e.receivedAt BETWEEN :from AND :to GROUP BY e.status")
    List<Object[]> countByStatusBetween(@Param("from") Long from, @Param("to") Long to);

    @Query("SELECT e.restaurantId, e.status, COUNT(e) FROM EasebuzzWebhookEvent e WHERE e.receivedAt BETWEEN :from AND :to GROUP BY e.restaurantId, e.status")
    List<Object[]> countByRestaurantAndStatusBetween(@Param("from") Long from, @Param("to") Long to);

    @Query("SELECT e FROM EasebuzzWebhookEvent e WHERE e.receivedAt BETWEEN :from AND :to ORDER BY e.receivedAt ASC")
    List<EasebuzzWebhookEvent> findByReceivedAtBetween(@Param("from") Long from, @Param("to") Long to);

    @Query("SELECT e FROM EasebuzzWebhookEvent e WHERE e.status IN ('failure', 'failed') ORDER BY e.receivedAt DESC")
    List<EasebuzzWebhookEvent> findFailedTransactions();

    @Query("SELECT e FROM EasebuzzWebhookEvent e WHERE e.status IN ('failure', 'failed') AND e.receivedAt >= :since ORDER BY e.receivedAt DESC")
    List<EasebuzzWebhookEvent> findFailedTransactionsSince(@Param("since") Long since);
}
