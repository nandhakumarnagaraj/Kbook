package com.khanabook.saas.repository;

import com.khanabook.saas.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByRestaurantIdAndCreatedAtBetween(Long restaurantId, Long from, Long to);

    List<StockMovement> findByRestaurantIdAndRawMaterialIdOrderByCreatedAtDesc(
            Long restaurantId, Long rawMaterialId);

    @Query("SELECT COALESCE(SUM(m.quantity), 0) FROM StockMovement m " +
           "WHERE m.restaurantId = :restaurantId AND m.rawMaterial.id = :rawMaterialId " +
           "AND m.kind IN ('SALES_DEDUCT','WASTAGE') AND m.createdAt >= :from")
    java.math.BigDecimal sumConsumedSince(@Param("restaurantId") Long restaurantId,
                                          @Param("rawMaterialId") Long rawMaterialId,
                                          @Param("from") Long from);
}
