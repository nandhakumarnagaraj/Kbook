package com.khanabook.saas.feature.inventory.data;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @deprecated Retired in V127. Customer data is stored directly on bills.
 */
@Deprecated
@Repository
public class CustomerProfileRepository {

    public Optional<CustomerProfile> findByRestaurantIdAndPhoneHash(Long restaurantId, String phoneHash) {
        return Optional.empty();
    }

    public List<CustomerProfile> findByRestaurantIdOrderByTotalSpendDesc(Long restaurantId) {
        return Collections.emptyList();
    }

    public List<Object[]> countBySegmentForRestaurant(Long restaurantId) {
        return Collections.emptyList();
    }

    public long countRepeatCustomers(Long restaurantId, long minOrders) {
        return 0L;
    }

    public BigDecimal avgOrderValueForRestaurant(Long restaurantId) {
        return BigDecimal.ZERO;
    }

    public <S extends CustomerProfile> S save(S entity) {
        return entity;
    }

    public Optional<CustomerProfile> findById(Long id) {
        return Optional.empty();
    }

    public void deleteById(Long id) {}

    public void deleteAll() {}
}
