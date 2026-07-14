package com.leo.erp.sales.order.service;

import com.leo.erp.system.printtemplate.service.PrintItemOptions;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SalesOrderPrintXlsxOptions(
        boolean hideUnitPrice,
        boolean hideRemark,
        String brandOverride,
        Map<String, String> brandOverrides,
        Map<String, String> brandOverridesByItemId,
        List<String> itemOrder,
        List<String> selectedItemIds
) {

    public SalesOrderPrintXlsxOptions {
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

    public static SalesOrderPrintXlsxOptions defaults() {
        return new SalesOrderPrintXlsxOptions(false, false, "", Map.of(), Map.of(), List.of(), null);
    }

    public PrintItemOptions itemOptions() {
        return new PrintItemOptions(brandOverride, brandOverrides, brandOverridesByItemId, itemOrder);
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
