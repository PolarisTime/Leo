package com.leo.erp.report.inventory.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.report.inventory.service.InventoryReportService;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory-report")
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;

    public InventoryReportController(InventoryReportService inventoryReportService) {
        this.inventoryReportService = inventoryReportService;
    }

    @GetMapping
    @RequiresPermission(resource = "inventory-report", action = "read")
    public ApiResponse<PageResponse<InventoryReportResponse>> page(
            @BindPageQuery(sortFieldKey = "inventory-report", directionParam = "sortDirection") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.success(PageResponse.from(inventoryReportService.page(query, keyword, warehouseName, category)));
    }
}
