package com.khanabook.saas.feature.compliance.data;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @deprecated Retired in V128. In-memory stub for FssaiTracker compatibility.
 */
@Deprecated
@Repository
public class FssaiTrackerRepository {

    private final Map<Long, FssaiTracker> store = new ConcurrentHashMap<>();

    public Optional<FssaiTracker> findByRestaurantId(Long restaurantId) {
        return Optional.ofNullable(store.get(restaurantId));
    }

    public Optional<FssaiTracker> findById(Long id) {
        return store.values().stream().filter(t -> id != null && id.equals(t.getId())).findFirst();
    }

    public <S extends FssaiTracker> S save(S entity) {
        if (entity != null && entity.getRestaurantId() != null) {
            store.put(entity.getRestaurantId(), entity);
        }
        return entity;
    }

    public <S extends FssaiTracker> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public void deleteById(Long id) {
        store.values().removeIf(t -> id != null && id.equals(t.getId()));
    }

    public void deleteAll() {
        store.clear();
    }
}
