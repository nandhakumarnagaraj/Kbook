package com.khanabook.saas.repository;

import com.khanabook.saas.entity.RawMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {

    List<RawMaterial> findByRestaurantIdAndIsDeletedFalseOrderByNameAsc(Long restaurantId);

    Optional<RawMaterial> findByRestaurantIdAndNameAndIsDeletedFalse(Long restaurantId, String name);

    List<RawMaterial> findByRestaurantIdAndStockQuantityLessThanEqualAndLowStockThresholdGreaterThan(
            Long restaurantId, java.math.BigDecimal stockQuantity, java.math.BigDecimal lowStockThreshold);
}
