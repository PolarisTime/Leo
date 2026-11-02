package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 打印明细的稳定排序（白名单字段 + 空值排最后）。
 *
 * <p>从 {@code PrintScriptService} 抽出：排序只依赖 {@link PageQuery} 与明细行，
 * 不含业务状态；白名单字段沿用原来的 8 个，未知字段仍然显式拒绝（返回 422 而不是静默忽略）。</p>
 */
final class PrintItemSorting {

    private static final Map<String, Comparator<PrintRecordItem>> COMPARATORS = Map.ofEntries(
            Map.entry("id", compareBy(PrintRecordItem::id)),
            Map.entry("recordId", compareBy(PrintRecordItem::recordId)),
            Map.entry("sourceNo", compareBy(PrintRecordItem::sourceNo)),
            Map.entry("deliveryDate", compareBy(PrintRecordItem::deliveryDate)),
            Map.entry("customerName", compareBy(PrintRecordItem::customerName)),
            Map.entry("projectName", compareBy(PrintRecordItem::projectName)),
            Map.entry("material", compareBy(PrintRecordItem::material)),
            Map.entry("spec", compareBy(PrintRecordItem::spec))
    );

    private PrintItemSorting() {
    }

    /**
     * 按 query 的 sortBy/direction 排序；sortBy 为空时保持数据库返回顺序。
     *
     * @throws BusinessException sortBy 不在白名单时（VALIDATION_ERROR）
     */
    static List<PrintRecordItem> sort(List<PrintRecordItem> items, PageQuery query) {
        String sortBy = query.sortBy();
        if (sortBy == null || sortBy.isBlank()) {
            return items;
        }
        Comparator<PrintRecordItem> comparator = COMPARATORS.get(sortBy);
        if (comparator == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "打印明细不支持按字段排序: " + sortBy);
        }
        if ("asc".equalsIgnoreCase(query.direction())) {
            return items.stream().sorted(comparator).toList();
        }
        return items.stream().sorted(comparator.reversed()).toList();
    }

    private static <T extends Comparable<T>> Comparator<PrintRecordItem> compareBy(Function<PrintRecordItem, T> key) {
        return Comparator.comparing(key, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
