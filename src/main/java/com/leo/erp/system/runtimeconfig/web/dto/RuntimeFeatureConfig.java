package com.leo.erp.system.runtimeconfig.web.dto;

public record RuntimeFeatureConfig(
        boolean weightOnlyPurchaseInbound,
        boolean weightOnlySalesOutbound
) {
}
