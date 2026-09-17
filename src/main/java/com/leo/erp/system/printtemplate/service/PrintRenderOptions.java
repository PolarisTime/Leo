package com.leo.erp.system.printtemplate.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PrintRenderOptions(
        boolean hideUnitPrice,
        boolean hideRemark,
        boolean mergeEquivalentItems,
        String brandOverride,
        Map<String, String> brandOverrides,
        Map<String, String> brandOverridesByItemId,
        List<String> itemOrder,
        List<String> selectedItemIds,
        Integer splitPieceCount,
        List<String> splitItemIds
) {

    public PrintRenderOptions {
        PrintItemOptions itemOptions = new PrintItemOptions(
                brandOverride,
                brandOverrides,
                brandOverridesByItemId,
                itemOrder
        );
        brandOverride = itemOptions.brandOverride();
        brandOverrides = itemOptions.brandOverrides();
        brandOverridesByItemId = itemOptions.brandOverridesByItemId();
        itemOrder = itemOptions.itemOrder();
        selectedItemIds = normalizeSelectedItemIds(selectedItemIds);
        splitPieceCount = normalizeSplitPieceCount(splitPieceCount);
        splitItemIds = normalizeSplitItemIds(splitItemIds);
    }

    public static PrintRenderOptions defaults() {
        return new PrintRenderOptions(false, false, true, "", Map.of(), Map.of(), List.of(), null, null, null);
    }

    public static PrintRenderOptions from(Object rawOptions) {
        if (rawOptions instanceof PrintRenderOptions options) {
            return options;
        }
        if (!(rawOptions instanceof Map<?, ?> options)) {
            return defaults();
        }
        PrintItemOptions itemOptions = PrintItemOptions.from(rawOptions);
        return new PrintRenderOptions(
                Boolean.TRUE.equals(options.get("hideUnitPrice")),
                Boolean.TRUE.equals(options.get("hideRemark")),
                !Boolean.FALSE.equals(options.get("mergeEquivalentItems")),
                itemOptions.brandOverride(),
                itemOptions.brandOverrides(),
                itemOptions.brandOverridesByItemId(),
                itemOptions.itemOrder(),
                options.containsKey("selectedItemIds") ? rawSelectedItemIds(options.get("selectedItemIds")) : null,
                rawSplitPieceCount(options.get("splitPieceCount")),
                options.containsKey("splitItemIds") ? rawSelectedItemIds(options.get("splitItemIds")) : null
        );
    }

    public PrintItemOptions itemOptions() {
        return new PrintItemOptions(brandOverride, brandOverrides, brandOverridesByItemId, itemOrder);
    }

    private static List<String> rawSelectedItemIds(Object rawValue) {
        if (!(rawValue instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private static List<String> normalizeSelectedItemIds(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    /**
     * {@code splitItemIds} 为 {@code null} 表示兼容旧语义（拆分所有明细）；
     * 非空集合表示仅拆分列出的明细行，空集合表示没有任何行需要拆分。
     */
    private static List<String> normalizeSplitItemIds(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static Integer normalizeSplitPieceCount(Integer value) {
        return value == null || value < 1 ? null : value;
    }

    /** 兼容 JSON 数字与字符串形式，非法或 &lt;1 一律视为不拆分（null）。 */
    private static Integer rawSplitPieceCount(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String text = String.valueOf(rawValue).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return normalizeSplitPieceCount(new BigDecimal(text).intValueExact());
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }
}
