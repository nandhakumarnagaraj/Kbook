package com.khanabook.saas.feature.inventory.data;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @deprecated Retired in V127. POS operates on finished MenuItem and ItemVariant catalog records.
 */
@Deprecated
@Repository
public class VendorRepository {

    public List<Vendor> findByRestaurantIdAndIsDeletedFalseOrderByNameAsc(Long restaurantId) {
        return Collections.emptyList();
    }

    public Optional<Vendor> findById(Long id) {
        return Optional.empty();
    }

    public <S extends Vendor> S save(S entity) {
        return entity;
    }

    public void deleteById(Long id) {}

    public void deleteAll() {}
}
