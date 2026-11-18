package com.khanabook.saas.feature.payments.data;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @deprecated Retired in V129. In-memory stub for Chargeback compatibility.
 */
@Deprecated
@Repository
public class ChargebackRepository {

    private final Map<Long, Chargeback> store = new ConcurrentHashMap<>();

    public List<Chargeback> findAll() {
        return List.copyOf(store.values());
    }

    public List<Chargeback> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, String status) {
        if (restaurantId == null || status == null) return Collections.emptyList();
        return store.values().stream()
                .filter(c -> restaurantId.equals(c.getRestaurantId()) && status.equals(c.getStatus()))
                .collect(Collectors.toList());
    }

    public List<Chargeback> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId) {
        if (restaurantId == null) return Collections.emptyList();
        return store.values().stream()
                .filter(c -> restaurantId.equals(c.getRestaurantId()))
                .collect(Collectors.toList());
    }

    public BigDecimal sumUnresolvedAmountByRestaurant(Long restaurantId) {
        return BigDecimal.ZERO;
    }

    public List<Object[]> countByStatusForRestaurant(Long restaurantId) {
        return Collections.emptyList();
    }

    public List<Object[]> countByReasonCodeForRestaurant(Long restaurantId) {
        return Collections.emptyList();
    }

    public Optional<Chargeback> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public <S extends Chargeback> S save(S entity) {
        if (entity != null) {
            if (entity.getId() == null) {
                entity.setId((long) (store.size() + 1));
            }
            store.put(entity.getId(), entity);
        }
        return entity;
    }

    public <S extends Chargeback> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public long count() {
        return store.size();
    }

    public void delete(Chargeback entity) {
        if (entity != null && entity.getId() != null) {
            store.remove(entity.getId());
        }
    }

    public void deleteById(Long id) {
        store.remove(id);
    }

    public void deleteAll(Iterable<? extends Chargeback> entities) {
        if (entities != null) {
            for (Chargeback entity : entities) {
                delete(entity);
            }
        }
    }

    public void deleteAll() {
        store.clear();
    }
}
