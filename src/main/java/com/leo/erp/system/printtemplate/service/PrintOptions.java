package com.leo.erp.system.printtemplate.service;

import java.util.Map;

public record PrintOptions(
        boolean hideUnitPrice,
        String brandOverride,
        Map<String, String> brandOverrides,
        Map<String, String> brandOverridesByItemId
) {

    public static PrintOptions defaults() {
        return new PrintOptions(false, "", Map.of(), Map.of());
    }
}
