package com.khanabook.saas.feature.payments.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @deprecated Retired in V129. In-memory stub for EasebuzzWebhookEvent compatibility.
 */
@Deprecated
@Repository
public class EasebuzzWebhookEventRepository {

    private final Map<Long, EasebuzzWebhookEvent> store = new ConcurrentHashMap<>();

    public Optional<EasebuzzWebhookEvent> findByRestaurantIdAndTxnId(Long restaurantId, String txnId) {
        if (restaurantId == null || txnId == null) return Optional.empty();
        return store.values().stream()
                .filter(e -> restaurantId.equals(e.getRestaurantId()) && txnId.equals(e.getTxnId()))
                .findFirst();
    }

    public Optional<EasebuzzWebhookEvent> findFirstByRestaurantIdAndTxnIdAndEasebuzzIdNotNullOrderByReceivedAtDesc(
            Long restaurantId, String txnId) {
        if (restaurantId == null || txnId == null) return Optional.empty();
        return store.values().stream()
                .filter(e -> restaurantId.equals(e.getRestaurantId()) && txnId.equals(e.getTxnId()) && e.getEasebuzzId() != null)
                .findFirst();
    }

    public boolean existsByTxnIdAndStatus(String txnId, String status) {
        if (txnId == null || status == null) return false;
        return store.values().stream().anyMatch(e -> txnId.equals(e.getTxnId()) && status.equalsIgnoreCase(e.getStatus()));
    }

    public long countByReceivedAtBetweenAndStatus(Long from, Long to, String status) {
        return 0L;
    }

    public long countByReceivedAtBetween(Long from, Long to) {
        return 0L;
    }

    public BigDecimal sumAmountByReceivedAtBetweenAndStatus(Long from, Long to, String status) {
        return BigDecimal.ZERO;
    }

    public List<Object[]> countGroupedByRestaurantIdBetween(Long from, Long to) {
        return Collections.emptyList();
    }

    public List<Object[]> countByStatusBetween(Long from, Long to) {
        return Collections.emptyList();
    }

    public List<Object[]> countByRestaurantAndStatusBetween(Long from, Long to) {
        return Collections.emptyList();
    }

    public List<EasebuzzWebhookEvent> findByReceivedAtBetween(Long from, Long to) {
        return Collections.emptyList();
    }

    public List<EasebuzzWebhookEvent> findFailedTransactions() {
        return Collections.emptyList();
    }

    public long findLatestReceivedAt() {
        return 0L;
    }

    public List<EasebuzzWebhookEvent> findFailedTransactionsSince(Long since) {
        return Collections.emptyList();
    }

    public Optional<EasebuzzWebhookEvent> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public <S extends EasebuzzWebhookEvent> S save(S entity) {
        if (entity != null) {
            if (entity.getId() == null) {
                entity.setId((long) (store.size() + 1));
            }
            store.put(entity.getId(), entity);
        }
        return entity;
    }

    public <S extends EasebuzzWebhookEvent> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public long count() {
        return store.size();
    }

    public List<EasebuzzWebhookEvent> findAll() {
        return List.copyOf(store.values());
    }

    public Page<EasebuzzWebhookEvent> findAll(Pageable pageable) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    public Page<EasebuzzWebhookEvent> findAll(Specification<EasebuzzWebhookEvent> spec, Pageable pageable) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    public void delete(EasebuzzWebhookEvent entity) {
        if (entity != null && entity.getId() != null) {
            store.remove(entity.getId());
        }
    }

    public void deleteById(Long id) {
        store.remove(id);
    }

    public void deleteAll(Iterable<? extends EasebuzzWebhookEvent> entities) {
        if (entities != null) {
            for (EasebuzzWebhookEvent entity : entities) {
                delete(entity);
            }
        }
    }

    public void deleteAll() {
        store.clear();
    }
}
