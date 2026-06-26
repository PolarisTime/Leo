package com.leo.erp.sales.order.service;

import com.leo.erp.system.printtemplate.service.PrintItemOptions;

import java.util.List;
import java.util.Map;

public record SalesOrderPrintXlsxOptions(
        boolean hideUnitPrice,
        boolean hideRemark,
        String brandOverride,
        Map<String, String> brandOverrides,
        Map<String, String> brandOverridesByItemId,
        List<String> itemOrder
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
    }

    public static SalesOrderPrintXlsxOptions defaults() {
        return new SalesOrderPrintXlsxOptions(false, false, "", Map.of(), Map.of(), List.of());
    }

    public PrintItemOptions itemOptions() {
        return new PrintItemOptions(brandOverride, brandOverrides, brandOverridesByItemId, itemOrder);
    }
}
