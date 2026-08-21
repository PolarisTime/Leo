package com.leo.erp.common.support;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * 源单行项目变更比对支撑：供采购、销售等下游变更守卫复用，
 * 统一"明细 ID 提取 + 行序归一 + 逐字段比较"的判定规则，
 * 避免各业务域守卫规则漂移。
 */
public final class SourceLineDiffSupport {

    private SourceLineDiffSupport() {
    }

    /** 提取源单明细 ID（去空、去重、升序），用于下游引用与占用锁检查。 */
    public static <T> List<Long> sourceItemIds(
            Collection<T> items,
            Function<T, Long> idGetter
    ) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(idGetter)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 判定源单行集合是否发生变更：按行号归一两侧后逐位比较，
     * 行数不同或任一行不一致即视为变更。
     */
    public static <C, N> boolean sourceLinesChanged(
            Collection<C> currentItems,
            Collection<N> requestedItems,
            Function<C, Integer> lineNoGetter,
            BiPredicate<C, N> sameLine
    ) {
        List<C> current = currentItems == null
                ? List.of()
                : currentItems.stream()
                .sorted(Comparator.comparing(lineNoGetter))
                .toList();
        List<N> next = requestedItems == null || requestedItems.isEmpty()
                ? List.of()
                : List.copyOf(requestedItems);
        if (current.size() != next.size()) {
            return true;
        }
        for (int index = 0; index < current.size(); index++) {
            if (!sameLine.test(current.get(index), next.get(index))) {
                return true;
            }
        }
        return false;
    }

    public static boolean sameText(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean sameNumber(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    public static boolean sameOptionalNumber(BigDecimal current, BigDecimal requested) {
        return requested == null || sameNumber(current, requested);
    }
}
