package com.leo.erp.system.printtemplate.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record PrintItemOptions(
        String brandOverride,
        Map<String, String> brandOverrides,
        Map<String, String> brandOverridesByItemId,
        List<String> itemOrder
) {

    public PrintItemOptions {
        brandOverride = normalizeText(brandOverride);
        brandOverrides = normalizeMap(brandOverrides);
        brandOverridesByItemId = normalizeMap(brandOverridesByItemId);
        itemOrder = normalizeList(itemOrder);
    }

    public static PrintItemOptions defaults() {
        return new PrintItemOptions("", Map.of(), Map.of(), List.of());
    }

    public static PrintItemOptions from(Object rawOptions) {
        if (!(rawOptions instanceof Map<?, ?> options)) {
            return defaults();
        }
        Object brandOverride = options.get("brandOverride");
        return new PrintItemOptions(
                brandOverride instanceof String value ? value : "",
                rawMap(options.get("brandOverrides")),
                rawMap(options.get("brandOverridesByItemId")),
                rawList(options.get("itemOrder"))
        );
    }

    private static Map<String, String> rawMap(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> values)) {
            return Map.of();
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof String)
                .collect(Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> String.valueOf(entry.getValue()),
                        (left, ignored) -> left
                ));
    }

    private static List<String> rawList(Object rawValue) {
        if (!(rawValue instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> normalizeMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return values.entrySet().stream()
                .map(entry -> Map.entry(normalizeText(entry.getKey()), normalizeText(entry.getValue())))
                .filter(entry -> !entry.getKey().isBlank() && !entry.getValue().isBlank())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (left, ignored) -> left));
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
