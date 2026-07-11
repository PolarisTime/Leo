package com.leo.erp.statement.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class StatementSourceCoverageValidator {

    private StatementSourceCoverageValidator() {
    }

    public static void requireAllEffectiveItems(String sourceDescription,
                                                Collection<Long> effectiveItemIds,
                                                Collection<Long> requestedItemIds) {
        Set<Long> effectiveIds = normalizeIds(effectiveItemIds);
        if (effectiveIds.isEmpty()) {
            return;
        }
        if (!normalizeIds(requestedItemIds).containsAll(effectiveIds)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    sourceDescription + "必须导入全部有效明细"
            );
        }
    }

    private static Set<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }
}
