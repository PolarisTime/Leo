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
        List<String> selectedItemIds,
        Integer splitPieceCount
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
        splitPieceCount = normalizeSplitPieceCount(splitPieceCount);
    }

    public static SalesOrderPrintXlsxOptions defaults() {
        return new SalesOrderPrintXlsxOptions(false, false, "", Map.of(), Map.of(), List.of(), null, null);
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

    private static Integer normalizeSplitPieceCount(Integer value) {
        return value == null || value < 1 ? null : value;
    }
}
