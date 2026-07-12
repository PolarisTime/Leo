package com.leo.erp.report.inventory.web.dto;

public record InventoryReportExportRequest(
        String keyword,
        Long warehouseId,
        String category,
        Boolean includeOutbound
) {
}
