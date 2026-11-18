package com.khanabook.saas.feature.payments.data;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @deprecated Retired in V129. In-memory stub for EasebuzzPayout compatibility.
 */
@Deprecated
@Repository
public class EasebuzzPayoutRepository {

    private final Map<Long, EasebuzzPayout> store = new ConcurrentHashMap<>();

    public List<EasebuzzPayout> findAll() {
        return List.copyOf(store.values());
    }

    public Optional<EasebuzzPayout> findByMerchantRequestId(String merchantRequestId) {
        if (merchantRequestId == null) return Optional.empty();
        return store.values().stream().filter(p -> merchantRequestId.equals(p.getMerchantRequestId())).findFirst();
    }

    public Optional<EasebuzzPayout> findByPayoutId(String payoutId) {
        if (payoutId == null) return Optional.empty();
        return store.values().stream().filter(p -> payoutId.equals(p.getPayoutId())).findFirst();
    }

    public List<EasebuzzPayout> findByRestaurantId(Long restaurantId) {
        if (restaurantId == null) return Collections.emptyList();
        return store.values().stream().filter(p -> restaurantId.equals(p.getRestaurantId())).collect(Collectors.toList());
    }

    public Optional<EasebuzzPayout> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public <S extends EasebuzzPayout> S save(S entity) {
        if (entity != null) {
            if (entity.getId() == null) {
                entity.setId((long) (store.size() + 1));
            }
            store.put(entity.getId(), entity);
        }
        return entity;
    }

    public <S extends EasebuzzPayout> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public long count() {
        return store.size();
    }

    public void delete(EasebuzzPayout entity) {
        if (entity != null && entity.getId() != null) {
            store.remove(entity.getId());
        }
    }

    public void deleteById(Long id) {
        store.remove(id);
    }

    public void deleteAll(Iterable<? extends EasebuzzPayout> entities) {
        if (entities != null) {
            for (EasebuzzPayout entity : entities) {
                delete(entity);
            }
        }
    }

    public void deleteAll() {
        store.clear();
    }
}
