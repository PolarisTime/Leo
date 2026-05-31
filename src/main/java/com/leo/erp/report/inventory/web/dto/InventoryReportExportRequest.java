package com.leo.erp.report.inventory.web.dto;

public record InventoryReportExportRequest(
        String keyword,
        String warehouseName,
        String category
) {
}
