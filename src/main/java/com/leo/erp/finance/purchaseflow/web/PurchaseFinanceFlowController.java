package com.leo.erp.finance.purchaseflow.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.purchaseflow.service.PurchaseFinanceFlowQueryService;
import com.leo.erp.finance.purchaseflow.web.dto.PurchaseFinanceDocumentFlowResponse;
import com.leo.erp.finance.purchaseflow.web.dto.PurchaseFinanceFlowFilter;
import com.leo.erp.security.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/purchase-finance")
@Tag(name = "采购财务单据流")
public class PurchaseFinanceFlowController {

    private final PurchaseFinanceFlowQueryService service;

    public PurchaseFinanceFlowController(PurchaseFinanceFlowQueryService service) {
        this.service = service;
    }

    @GetMapping("/document-flow")
    @RequiresPermission(resource = "receivable-payable", action = "read")
    @Operation(summary = "按结算主体和供应商查询采购财务逐行单据流")
    public ApiResponse<PurchaseFinanceDocumentFlowResponse> query(
            @NotNull @Positive @RequestParam Long settlementCompanyId,
            @NotNull @Positive @RequestParam Long supplierId,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String materialKeyword,
            @Positive @RequestParam(required = false) Long purchaseOrderId,
            @BindPageQuery(sortFieldKey = "purchase-finance-flow") PageQuery pageQuery
    ) {
        return ApiResponse.success(service.query(
                new PurchaseFinanceFlowFilter(
                        settlementCompanyId,
                        supplierId,
                        documentType,
                        status,
                        startDate,
                        endDate,
                        materialKeyword,
                        purchaseOrderId
                ),
                pageQuery
        ));
    }

    @PostMapping("/document-flow/export")
    @RequiresPermission(resource = "receivable-payable", action = "export")
    @Operation(summary = "按当前筛选口径导出采购财务逐行单据流")
    public ResponseEntity<byte[]> export(@Valid @RequestBody PurchaseFinanceFlowFilter filter) {
        FileDownloadResponse file = service.exportExcel(filter);
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .contentLength(file.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(file.content());
    }
}
