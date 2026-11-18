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
public class StockMovementRepository {

    public List<StockMovement> findByRestaurantIdAndCreatedAtBetween(Long restaurantId, Long from, Long to) {
        return Collections.emptyList();
    }

    public List<StockMovement> findByRestaurantIdAndRawMaterialIdOrderByCreatedAtDesc(Long restaurantId, Long rawMaterialId) {
        return Collections.emptyList();
    }

    public BigDecimal sumConsumedSince(Long restaurantId, Long rawMaterialId, Long from) {
        return BigDecimal.ZERO;
    }

    public Optional<StockMovement> findById(Long id) {
        return Optional.empty();
    }

    public <S extends StockMovement> S save(S entity) {
        return entity;
    }

    public <S extends StockMovement> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public void deleteById(Long id) {}

    public void deleteAll() {}
}
