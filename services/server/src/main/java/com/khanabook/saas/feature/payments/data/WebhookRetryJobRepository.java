package com.khanabook.saas.feature.payments.data;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @deprecated Retired in V129. In-memory stub for WebhookRetryJob compatibility.
 */
@Deprecated
@Repository
public class WebhookRetryJobRepository {

    private final Map<Long, WebhookRetryJob> store = new ConcurrentHashMap<>();

    public List<WebhookRetryJob> findAll() {
        return List.copyOf(store.values());
    }

    public List<WebhookRetryJob> findByStatusAndNextAttemptAtLessThanEqual(String status, long nextAttemptAt) {
        if (status == null) return Collections.emptyList();
        return store.values().stream()
                .filter(w -> status.equals(w.getStatus()) && w.getNextAttemptAt() <= nextAttemptAt)
                .collect(Collectors.toList());
    }

    public Optional<WebhookRetryJob> findByJobKey(String jobKey) {
        if (jobKey == null) return Optional.empty();
        return store.values().stream().filter(w -> jobKey.equals(w.getJobKey())).findFirst();
    }

    public List<Object[]> countByStatusGrouped() {
        return Collections.emptyList();
    }

    public List<Object[]> countByTypeAndStatusGrouped() {
        return Collections.emptyList();
    }

    public long countByStatus(String status) {
        if (status == null) return 0L;
        return store.values().stream().filter(w -> status.equals(w.getStatus())).count();
    }

    public List<WebhookRetryJob> findByStatusOrderByCreatedAtDesc(String status) {
        if (status == null) return Collections.emptyList();
        return store.values().stream().filter(w -> status.equals(w.getStatus())).collect(Collectors.toList());
    }

    public Optional<WebhookRetryJob> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public <S extends WebhookRetryJob> S save(S entity) {
        if (entity != null) {
            if (entity.getId() == null) {
                entity.setId((long) (store.size() + 1));
            }
            store.put(entity.getId(), entity);
        }
        return entity;
    }

    public <S extends WebhookRetryJob> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public long count() {
        return store.size();
    }

    public void delete(WebhookRetryJob entity) {
        if (entity != null && entity.getId() != null) {
            store.remove(entity.getId());
        }
    }

    public void deleteById(Long id) {
        store.remove(id);
    }

    public void deleteAll(Iterable<? extends WebhookRetryJob> entities) {
        if (entities != null) {
            for (WebhookRetryJob entity : entities) {
                delete(entity);
            }
        }
    }

    public void deleteAll() {
        store.clear();
    }
}
