package com.khanabook.saas.feature.payments.data;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @deprecated Retired in V129. In-memory stub for EasebuzzSubMerchant compatibility.
 */
@Deprecated
@Repository
public class EasebuzzSubMerchantRepository {

    private final Map<Long, EasebuzzSubMerchant> store = new ConcurrentHashMap<>();

    public Optional<EasebuzzSubMerchant> findByRestaurantId(Long restaurantId) {
        if (restaurantId == null) return Optional.empty();
        return store.values().stream().filter(sm -> restaurantId.equals(sm.getRestaurantId())).findFirst();
    }

    public Optional<EasebuzzSubMerchant> findBySubMerchantId(String subMerchantId) {
        if (subMerchantId == null) return Optional.empty();
        return store.values().stream().filter(sm -> subMerchantId.equals(sm.getSubMerchantId())).findFirst();
    }

    public List<EasebuzzSubMerchant> findAllByContactEmail(String contactEmail) {
        if (contactEmail == null) return Collections.emptyList();
        return store.values().stream().filter(sm -> contactEmail.equalsIgnoreCase(sm.getContactEmail())).collect(Collectors.toList());
    }

    public List<EasebuzzSubMerchant> findByStatus(String status) {
        if (status == null) return Collections.emptyList();
        return store.values().stream().filter(sm -> status.equalsIgnoreCase(sm.getStatus())).collect(Collectors.toList());
    }

    public boolean existsByRestaurantId(Long restaurantId) {
        if (restaurantId == null) return false;
        return store.values().stream().anyMatch(sm -> restaurantId.equals(sm.getRestaurantId()));
    }

    public List<EasebuzzSubMerchant> findAll() {
        return List.copyOf(store.values());
    }

    public Optional<EasebuzzSubMerchant> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public <S extends EasebuzzSubMerchant> S save(S entity) {
        if (entity != null) {
            if (entity.getId() == null) {
                entity.setId((long) (store.size() + 1));
            }
            store.put(entity.getId(), entity);
        }
        return entity;
    }

    public <S extends EasebuzzSubMerchant> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public void delete(EasebuzzSubMerchant entity) {
        if (entity != null && entity.getId() != null) {
            store.remove(entity.getId());
        }
    }

    public void deleteById(Long id) {
        store.remove(id);
    }

    public void deleteAll(Iterable<? extends EasebuzzSubMerchant> entities) {
        if (entities != null) {
            for (EasebuzzSubMerchant entity : entities) {
                delete(entity);
            }
        }
    }

    public void deleteAll() {
        store.clear();
    }
}
