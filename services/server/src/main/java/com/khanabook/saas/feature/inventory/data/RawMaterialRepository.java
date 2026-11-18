package com.khanabook.saas.feature.inventory.data;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @deprecated Retired in V127. POS operates on finished MenuItem and ItemVariant catalog records.
 */
@Deprecated
@Repository
public class RawMaterialRepository {

    public List<RawMaterial> findByRestaurantIdAndIsDeletedFalseOrderByNameAsc(Long restaurantId) {
        return Collections.emptyList();
    }

    public Optional<RawMaterial> findByRestaurantIdAndNameAndIsDeletedFalse(Long restaurantId, String name) {
        return Optional.empty();
    }

    public List<RawMaterial> findByRestaurantIdAndStockQuantityLessThanEqualAndLowStockThresholdGreaterThan(
            Long restaurantId, BigDecimal stockQuantity, BigDecimal lowStockThreshold) {
        return Collections.emptyList();
    }

    public Optional<RawMaterial> findById(Long id) {
        return Optional.empty();
    }

    public <S extends RawMaterial> S save(S entity) {
        return entity;
    }

    public <S extends RawMaterial> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public void deleteById(Long id) {}

    public void deleteAll() {}
}
