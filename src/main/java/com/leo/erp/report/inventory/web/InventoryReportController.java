package com.leo.erp.report.inventory.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.report.inventory.service.InventoryReportService;
import com.leo.erp.report.inventory.web.dto.InventoryReportExportRequest;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@Validated
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
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean includeOutbound
    ) {
        return ApiResponse.success(PageResponse.from(
                inventoryReportService.page(query, keyword, warehouseName, category, includeOutbound)
        ));
    }

    @PostMapping("/export")
    @RequiresPermission(resource = "inventory-report", action = "export")
    public ResponseEntity<byte[]> export(@RequestBody(required = false) InventoryReportExportRequest request) {
        InventoryReportExportRequest safeRequest = request == null
                ? new InventoryReportExportRequest(null, null, null, null)
                : request;
        return toDownloadResponse(inventoryReportService.exportExcel(
                safeRequest.keyword(),
                safeRequest.warehouseName(),
                safeRequest.category(),
                safeRequest.includeOutbound()
        ));
    }

    private ResponseEntity<byte[]> toDownloadResponse(FileDownloadResponse file) {
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .contentLength(file.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString()
                )
                .body(file.content());
    }
}
