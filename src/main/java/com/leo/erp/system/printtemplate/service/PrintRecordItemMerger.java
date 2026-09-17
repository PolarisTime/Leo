package com.leo.erp.system.printtemplate.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PrintRecordItemMerger {

    private static final List<String> SUM_FIELDS = List.of("quantity", "weightTon", "amount");

    private PrintRecordItemMerger() {
    }

    static List<Map<String, String>> mergeEquivalentItems(List<Map<String, String>> items) {
        return mergeEquivalentItems(items, null);
    }

    /**
     * 合并等价明细；若被并入的行勾选了拆分，则在合并行写入拆分标记，
     * 保证「逐行勾选拆分」在合并后仍然生效。
     */
    static List<Map<String, String>> mergeEquivalentItems(List<Map<String, String>> items,
                                                          Collection<String> splitItemIds) {
        if (items.size() < 2) {
            return items;
        }

        Set<String> selectedSplitItemIds = splitItemIds == null ? null : new HashSet<>(splitItemIds);
        Map<MergeKey, Map<String, String>> mergedItems = new LinkedHashMap<>();
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> item : items) {
            MergeKey key = MergeKey.from(item);
            if (!key.mergeable()) {
                result.add(item);
                continue;
            }

            Map<String, String> mergedItem = mergedItems.get(key);
            if (mergedItem == null) {
                mergedItem = new HashMap<>(item);
                mergedItems.put(key, mergedItem);
                result.add(mergedItem);
                continue;
            }

            for (String field : SUM_FIELDS) {
                mergedItem.put(field, sum(mergedItem.get(field), item.get(field)));
            }
            mergedItem.put("pieceWeightTon", "");
            if (selectedSplitItemIds != null && selectedSplitItemIds.contains(item.get("id"))) {
                mergedItem.put(PrintItemSplitter.SPLIT_REQUEST_FIELD, "true");
            }
        }
        return result;
    }

    private static String sum(String left, String right) {
        return decimal(left).add(decimal(right)).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record MergeKey(
            String brand,
            String category,
            String material,
            String spec,
            String length
    ) {
        private static MergeKey from(Map<String, String> item) {
            return new MergeKey(
                    normalize(item.get("brand")),
                    normalize(item.get("category")),
                    normalize(item.get("material")),
                    normalize(item.get("spec")),
                    normalize(item.get("length"))
            );
        }

        private boolean mergeable() {
            return !brand.isBlank() && !spec.isBlank() && !length.isBlank();
        }
    }
}
