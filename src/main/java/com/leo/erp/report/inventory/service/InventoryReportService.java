package com.leo.erp.report.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.report.inventory.repository.InventoryReportQueryRepository;
import com.leo.erp.report.inventory.web.dto.InventoryReportExportRow;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class InventoryReportService {

    private final InventoryReportQueryRepository inventoryReportQueryRepository;
    private final ExcelExportService excelExportService;

    public InventoryReportService(InventoryReportQueryRepository inventoryReportQueryRepository,
                                  ExcelExportService excelExportService) {
        this.inventoryReportQueryRepository = inventoryReportQueryRepository;
        this.excelExportService = excelExportService;
    }

    @Transactional(readOnly = true)
    public Page<InventoryReportResponse> page(PageQuery query, String keyword, String warehouseName, String category) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedWarehouseName = trimToNull(warehouseName);
        String normalizedCategory = trimToNull(category);
        return inventoryReportQueryRepository.page(query, normalizedKeyword, normalizedWarehouseName, normalizedCategory);
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportExcel(String keyword, String warehouseName, String category) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedWarehouseName = trimToNull(warehouseName);
        String normalizedCategory = trimToNull(category);
        var rows = inventoryReportQueryRepository.list(
                PageQuery.of(0, 200, null, null),
                normalizedKeyword,
                normalizedWarehouseName,
                normalizedCategory
        ).stream().map(InventoryReportExportRow::from).toList();
        byte[] data = excelExportService.export(rows, InventoryReportExportRow.class);
        return new FileDownloadResponse(
                "商品库存报表.xlsx",
                new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                data
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
