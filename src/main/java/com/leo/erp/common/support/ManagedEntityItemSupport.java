package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

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

    /**
     * Synchronizes an existing list of child entities with an incoming request list.
     * Items are matched by ID: existing items with an ID present in the request are
     * retained; items in the request without an ID create new entities; existing items
     * not referenced in the request are removed.
     *
     * @throws BusinessException if the request contains duplicate IDs
     */
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
        Set<Long> seenRequestIds = new LinkedHashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            R request = requests.get(i);
            Long requestId = requestIdGetter.apply(request);
            if (requestId != null) {
                // Reject duplicate IDs in the same request.
                if (!seenRequestIds.add(requestId)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "第" + (i + 1) + "行子项ID重复: " + requestId);
                }
                // Reject IDs that don't belong to an existing item (forged/expired).
                if (!existingById.containsKey(requestId)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "第" + (i + 1) + "行子项ID不存在: " + requestId);
                }
            }
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
