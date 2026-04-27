package com.leo.erp.common.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class ManagedEntityItemSupport {

    private ManagedEntityItemSupport() {
    }

    public static <E, R> List<E> syncById(List<E> existingItems,
                                          List<R> requests,
                                          Function<E, Long> entityIdGetter,
                                          Function<R, Long> requestIdGetter,
                                          Supplier<E> newEntitySupplier,
                                          LongSupplier nextIdSupplier,
                                          BiConsumer<E, Long> entityIdSetter) {
        Map<Long, E> existingById = new LinkedHashMap<>();
        for (E item : existingItems) {
            Long id = entityIdGetter.apply(item);
            if (id != null) {
                existingById.put(id, item);
            }
        }

        List<E> orderedItems = new ArrayList<>(requests.size());
        Set<E> retainedItems = new LinkedHashSet<>();
        for (R request : requests) {
            Long requestId = requestIdGetter.apply(request);
            E item = requestId == null ? null : existingById.get(requestId);
            if (item == null) {
                item = newEntitySupplier.get();
                entityIdSetter.accept(item, nextIdSupplier.getAsLong());
                existingItems.add(item);
            }
            orderedItems.add(item);
            retainedItems.add(item);
        }

        existingItems.removeIf(item -> !retainedItems.contains(item));
        return orderedItems;
    }
}
