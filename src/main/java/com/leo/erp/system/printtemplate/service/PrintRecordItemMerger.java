package com.leo.erp.system.printtemplate.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PrintRecordItemMerger {

    private static final List<String> SUM_FIELDS = List.of("quantity", "weightTon", "amount");

    private PrintRecordItemMerger() {
    }

    static List<Map<String, String>> mergeEquivalentItems(List<Map<String, String>> items) {
        if (items.size() < 2) {
            return items;
        }

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
