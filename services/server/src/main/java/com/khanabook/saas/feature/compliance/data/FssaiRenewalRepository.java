package com.khanabook.saas.feature.compliance.data;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @deprecated Retired in V128. In-memory stub for FssaiRenewal compatibility.
 */
@Deprecated
@Repository
public class FssaiRenewalRepository {

    private final Map<String, FssaiRenewal> storeByTxn = new ConcurrentHashMap<>();

    public Optional<FssaiRenewal> findByEasebuzzTxnId(String easebuzzTxnId) {
        if (easebuzzTxnId == null) return Optional.empty();
        return Optional.ofNullable(storeByTxn.get(easebuzzTxnId));
    }

    public List<FssaiRenewal> findByRestaurantId(Long restaurantId) {
        if (restaurantId == null) return Collections.emptyList();
        return storeByTxn.values().stream()
                .filter(r -> restaurantId.equals(r.getRestaurantId()))
                .collect(Collectors.toList());
    }

    public Optional<FssaiRenewal> findById(Long id) {
        return storeByTxn.values().stream().filter(r -> id != null && id.equals(r.getId())).findFirst();
    }

    public <S extends FssaiRenewal> S save(S entity) {
        if (entity != null && entity.getEasebuzzTxnId() != null) {
            storeByTxn.put(entity.getEasebuzzTxnId(), entity);
        }
        return entity;
    }

    public <S extends FssaiRenewal> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public void deleteById(Long id) {
        storeByTxn.values().removeIf(r -> id != null && id.equals(r.getId()));
    }

    public void deleteAll() {
        storeByTxn.clear();
    }
}
