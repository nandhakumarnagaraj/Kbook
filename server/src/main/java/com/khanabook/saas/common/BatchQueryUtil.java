package com.khanabook.saas.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public final class BatchQueryUtil {

    private static final int DEFAULT_BATCH_SIZE = 500;

    private BatchQueryUtil() {}

    public static <T, R> List<R> queryInBatches(Collection<T> ids, Function<List<T>, List<R>> queryFn) {
        return queryInBatches(ids, queryFn, DEFAULT_BATCH_SIZE);
    }

    public static <T, R> List<R> queryInBatches(Collection<T> ids, Function<List<T>, List<R>> queryFn, int batchSize) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<R> results = new ArrayList<>();
        List<T> idList = new ArrayList<>(ids);
        for (int i = 0; i < idList.size(); i += batchSize) {
            List<T> batch = idList.subList(i, Math.min(i + batchSize, idList.size()));
            results.addAll(queryFn.apply(batch));
        }
        return results;
    }
}
