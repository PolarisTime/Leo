package com.leo.erp.system.printtemplate.service;

import java.util.List;
import java.util.Map;

public record PrintRenderOptions(
        boolean hideUnitPrice,
        boolean hideRemark,
        String brandOverride,
        Map<String, String> brandOverrides,
        Map<String, String> brandOverridesByItemId,
        List<String> itemOrder
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
    }

    public static PrintRenderOptions defaults() {
        return new PrintRenderOptions(false, false, "", Map.of(), Map.of(), List.of());
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
                itemOptions.itemOrder()
        );
    }

    public PrintItemOptions itemOptions() {
        return new PrintItemOptions(brandOverride, brandOverrides, brandOverridesByItemId, itemOrder);
    }
}
