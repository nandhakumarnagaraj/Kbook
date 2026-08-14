package com.khanabook.saas.repository;

import com.khanabook.saas.entity.Chargeback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ChargebackRepository extends JpaRepository<Chargeback, Long> {

    List<Chargeback> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, String status);

    List<Chargeback> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM Chargeback c WHERE c.restaurantId = :restaurantId AND c.status != 'resolved'")
    BigDecimal sumUnresolvedAmountByRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT c.status, COUNT(c) FROM Chargeback c WHERE c.restaurantId = :restaurantId GROUP BY c.status")
    List<Object[]> countByStatusForRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT c.reasonCode, COUNT(c) FROM Chargeback c WHERE c.restaurantId = :restaurantId AND c.status != 'resolved' GROUP BY c.reasonCode")
    List<Object[]> countByReasonCodeForRestaurant(@Param("restaurantId") Long restaurantId);
}