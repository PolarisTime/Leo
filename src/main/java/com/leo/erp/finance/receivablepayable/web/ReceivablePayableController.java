package com.leo.erp.finance.receivablepayable.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.receivablepayable.service.ReceivablePayableService;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailResponse;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@Validated
@RequestMapping("/receivable-payables")
@Tag(name = "应收应付")
public class ReceivablePayableController {

    private final ReceivablePayableService receivablePayableService;

    public ReceivablePayableController(ReceivablePayableService receivablePayableService) {
        this.receivablePayableService = receivablePayableService;
    }

    @GetMapping
    @RequiresPermission(resource = "receivable-payable", action = "read")
    @Operation(summary = "分页查询应收应付账簿汇总")
    public ApiResponse<PageResponse<ReceivablePayableResponse>> page(
            @BindPageQuery(sortFieldKey = "receivable-payable", directionParam = "sortDirection") PageQuery query,
            @RequestParam(name = "direction", required = false) String businessDirection,
            @RequestParam(required = false) String counterpartyType,
            @RequestParam(required = false) String reconciliationStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(
                receivablePayableService.page(query, businessDirection, counterpartyType, reconciliationStatus, status, keyword)
        ));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "receivable-payable", action = "read")
    @Operation(summary = "查询应收应付账簿明细")
    public ApiResponse<ReceivablePayableDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.success(receivablePayableService.detail(id));
    }

    @PostMapping("/export")
    @RequiresPermission(resource = "receivable-payable", action = "export")
    @Operation(summary = "导出应收应付账簿汇总")
    public ResponseEntity<byte[]> export(
            @RequestParam(name = "direction", required = false) String businessDirection,
            @RequestParam(required = false) String counterpartyType,
            @RequestParam(required = false) String reconciliationStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return toDownloadResponse(receivablePayableService.exportExcel(
                businessDirection,
                counterpartyType,
                reconciliationStatus,
                status,
                keyword
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
