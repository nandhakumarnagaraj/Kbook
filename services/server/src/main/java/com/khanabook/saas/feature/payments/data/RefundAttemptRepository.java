package com.khanabook.saas.feature.payments.data;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @deprecated Retired in V129. In-memory stub for RefundAttempt compatibility.
 */
@Deprecated
@Repository
public class RefundAttemptRepository {

    private final Map<Long, RefundAttempt> store = new ConcurrentHashMap<>();

    public java.util.List<RefundAttempt> findAll() {
        return java.util.List.copyOf(store.values());
    }

    public Optional<RefundAttempt> findByBillIdAndGatewayRefundId(Long billId, String gatewayRefundId) {
        if (billId == null || gatewayRefundId == null) return Optional.empty();
        return store.values().stream()
                .filter(a -> billId.equals(a.getBillId()) && gatewayRefundId.equals(a.getGatewayRefundId()))
                .findFirst();
    }

    public BigDecimal sumAmountByBillIdAndStatus(Long billId, String status) {
        return BigDecimal.ZERO;
    }

    public Optional<RefundAttempt> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public <S extends RefundAttempt> S save(S entity) {
        if (entity != null) {
            if (entity.getId() == null) {
                entity.setId((long) (store.size() + 1));
            }
            store.put(entity.getId(), entity);
        }
        return entity;
    }

    public <S extends RefundAttempt> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public long count() {
        return store.size();
    }

    public void delete(RefundAttempt entity) {
        if (entity != null && entity.getId() != null) {
            store.remove(entity.getId());
        }
    }

    public void deleteById(Long id) {
        store.remove(id);
    }

    public void deleteAll(Iterable<? extends RefundAttempt> entities) {
        if (entities != null) {
            for (RefundAttempt entity : entities) {
                delete(entity);
            }
        }
    }

    public void deleteAll() {
        store.clear();
    }
}
