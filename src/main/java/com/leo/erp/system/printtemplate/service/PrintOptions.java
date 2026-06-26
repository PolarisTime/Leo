package com.leo.erp.system.printtemplate.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record PrintOptions(
        boolean hideUnitPrice,
        boolean hideRemark,
        String brandOverride,
        Map<String, String> brandOverrides,
        Map<String, String> brandOverridesByItemId,
        List<String> itemOrder
) {

    public static PrintOptions defaults() {
        return new PrintOptions(false, false, "", Map.of(), Map.of(), List.of());
    }

    public static PrintOptions from(Object rawOptions) {
        if (!(rawOptions instanceof Map<?, ?> options)) {
            return defaults();
        }
        Object hideUnitPrice = options.get("hideUnitPrice");
        Object hideRemark = options.get("hideRemark");
        Object brandOverride = options.get("brandOverride");
        return new PrintOptions(
                Boolean.TRUE.equals(hideUnitPrice),
                Boolean.TRUE.equals(hideRemark),
                brandOverride instanceof String value ? value.trim() : "",
                brandOverrides(options.get("brandOverrides")),
                brandOverrides(options.get("brandOverridesByItemId")),
                itemOrder(options.get("itemOrder"))
        );
    }

    private static Map<String, String> brandOverrides(Object rawBrandOverrides) {
        if (!(rawBrandOverrides instanceof Map<?, ?> values)) {
            return Map.of();
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof String)
                .map(entry -> Map.entry(
                        String.valueOf(entry.getKey()).trim(),
                        String.valueOf(entry.getValue()).trim()
                ))
                .filter(entry -> !entry.getKey().isBlank() && !entry.getValue().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, ignored) -> left));
    }

    private static List<String> itemOrder(Object rawItemOrder) {
        if (!(rawItemOrder instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> String.valueOf(value).trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
