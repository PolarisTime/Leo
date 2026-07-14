package com.leo.erp.system.printtemplate.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PrintRenderOptions(
        boolean hideUnitPrice,
        boolean hideRemark,
        String brandOverride,
        Map<String, String> brandOverrides,
        Map<String, String> brandOverridesByItemId,
        List<String> itemOrder,
        List<String> selectedItemIds
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
    }

    public static PrintRenderOptions defaults() {
        return new PrintRenderOptions(false, false, "", Map.of(), Map.of(), List.of(), null);
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
                itemOptions.brandOverride(),
                itemOptions.brandOverrides(),
                itemOptions.brandOverridesByItemId(),
                itemOptions.itemOrder(),
                options.containsKey("selectedItemIds") ? rawSelectedItemIds(options.get("selectedItemIds")) : null
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
}
