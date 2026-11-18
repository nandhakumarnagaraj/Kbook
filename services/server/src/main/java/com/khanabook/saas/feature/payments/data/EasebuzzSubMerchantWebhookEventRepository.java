package com.khanabook.saas.feature.payments.data;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @deprecated Retired in V129. In-memory stub for EasebuzzSubMerchantWebhookEvent compatibility.
 */
@Deprecated
@Repository
public class EasebuzzSubMerchantWebhookEventRepository {

    private final Map<Long, EasebuzzSubMerchantWebhookEvent> store = new ConcurrentHashMap<>();

    public List<EasebuzzSubMerchantWebhookEvent> findBySubMerchantIdOrderByReceivedAtDesc(String subMerchantId) {
        if (subMerchantId == null) return Collections.emptyList();
        return store.values().stream()
                .filter(e -> subMerchantId.equals(e.getSubMerchantId()))
                .collect(Collectors.toList());
    }

    public List<EasebuzzSubMerchantWebhookEvent> findAll() {
        return List.copyOf(store.values());
    }

    public long count() {
        return store.size();
    }

    public List<EasebuzzSubMerchantWebhookEvent> findByProcessedFalse() {
        return store.values().stream().filter(e -> Boolean.FALSE.equals(e.getProcessed())).collect(Collectors.toList());
    }

    public Optional<EasebuzzSubMerchantWebhookEvent> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public <S extends EasebuzzSubMerchantWebhookEvent> S save(S entity) {
        if (entity != null) {
            if (entity.getId() == null) {
                entity.setId((long) (store.size() + 1));
            }
            store.put(entity.getId(), entity);
        }
        return entity;
    }

    public <S extends EasebuzzSubMerchantWebhookEvent> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public void delete(EasebuzzSubMerchantWebhookEvent entity) {
        if (entity != null && entity.getId() != null) {
            store.remove(entity.getId());
        }
    }

    public void deleteById(Long id) {
        store.remove(id);
    }

    public void deleteAll(Iterable<? extends EasebuzzSubMerchantWebhookEvent> entities) {
        if (entities != null) {
            for (EasebuzzSubMerchantWebhookEvent entity : entities) {
                delete(entity);
            }
        }
    }

    public void deleteAll() {
        store.clear();
    }
}
