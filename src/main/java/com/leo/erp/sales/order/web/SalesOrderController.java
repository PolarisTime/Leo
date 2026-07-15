package com.leo.erp.sales.order.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.sales.order.service.SalesOrderPrintExportService;
import com.leo.erp.sales.order.service.SalesOrderPrintXlsxOptions;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.service.SalesOrderSourceCandidateService;
import com.leo.erp.sales.order.web.dto.SalesOrderPrintXlsxRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateResponse;
import com.leo.erp.system.operationlog.support.OperationLogResultCollector;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Tag(name = "销售订单")
@RestController
@Validated
@RequestMapping("/sales-orders")
public class SalesOrderController {

    private final SalesOrderService service;
    private final SalesOrderPrintExportService printExportService;
    private final SalesOrderSourceCandidateService sourceCandidateService;

    public SalesOrderController(SalesOrderService service,
                                SalesOrderPrintExportService printExportService,
                                SalesOrderSourceCandidateService sourceCandidateService) {
        this.service = service;
        this.printExportService = printExportService;
        this.sourceCandidateService = sourceCandidateService;
    }

    @Operation(summary = "分页查询销售订单采购来源候选")
    @GetMapping("/source-candidates")
    @RequiresPermission(resource = "purchase-order", action = "read")
    public ApiResponse<PageResponse<SalesOrderSourceCandidateResponse>> sourceCandidates(
            @BindPageQuery(sortFieldKey = "purchase-order") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long currentSalesOrderId
    ) {
        return ApiResponse.success(sourceCandidateService.page(
                keyword,
                supplierId,
                settlementCompanyId,
                startDate,
                endDate,
                currentSalesOrderId,
                query
        ));
    }

    @Operation(summary = "搜索销售订单")
    @GetMapping("/search")
    @RequiresPermission(resource = "sales-order", action = "read")
    public ApiResponse<java.util.List<SalesOrderResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @Operation(summary = "分页查询销售订单")
    @GetMapping
    @RequiresPermission(resource = "sales-order", action = "read")
    public ApiResponse<PageResponse<SalesOrderResponse>> page(
            @BindPageQuery(sortFieldKey = "sales-order") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                service.page(
                        query,
                        PageFilter.of(keyword, customerName, projectName, settlementCompanyId, status, startDate, endDate)
                                .withIdentity(customerId, projectId, null, null, null)
                )
        ));
    }

    @Operation(summary = "分页查询销售订单出库导入候选")
    @GetMapping("/outbound-import-candidates")
    @RequiresPermission(resource = "sales-order", action = "read")
    public ApiResponse<PageResponse<SalesOrderResponse>> outboundImportCandidates(
            @BindPageQuery(sortFieldKey = "sales-order") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long currentRecordId
    ) {
        return ApiResponse.success(PageResponse.from(
                service.outboundImportCandidates(
                        query,
                        PageFilter.of(keyword, customerName, projectName, settlementCompanyId, status, startDate, endDate)
                                .withIdentity(customerId, projectId, null, null, currentRecordId)
                )
        ));
    }

    @Operation(summary = "查询销售订单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "sales-order", action = "read")
    public ApiResponse<SalesOrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @Operation(summary = "导出销售订单套打 Excel")
    @GetMapping("/{id}/print-xlsx")
    @RequiresPermission(resource = "sales-order", action = "print")
    @OperationLoggable(moduleName = "销售订单", actionType = "打印", businessNoFields = {"id"}, recordIdField = "id")
    public ResponseEntity<byte[]> exportPrintXlsx(@PathVariable Long id, HttpServletRequest request) {
        return toDownloadResponse(printExportService.exportSalesOrderPrint(id, SalesOrderPrintXlsxOptions.defaults()), request);
    }

    @Operation(summary = "按打印选项导出销售订单套打 Excel")
    @PostMapping("/{id}/print-xlsx")
    @RequiresPermission(resource = "sales-order", action = "print")
    @OperationLoggable(moduleName = "销售订单", actionType = "打印", businessNoFields = {"id"}, recordIdField = "id")
    public ResponseEntity<byte[]> exportPrintXlsx(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) SalesOrderPrintXlsxRequest payload,
            HttpServletRequest request
    ) {
        SalesOrderPrintXlsxOptions options = payload == null
                ? SalesOrderPrintXlsxOptions.defaults()
                : payload.resolvedPrintOptions();
        return toDownloadResponse(printExportService.exportSalesOrderPrint(id, options), request);
    }

    @Operation(summary = "创建销售订单")
    @PostMapping
    @RequiresPermission(resource = "sales-order", action = "create")
    @DomainEventAudited
    public ApiResponse<SalesOrderResponse> create(@Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @Operation(summary = "更新销售订单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "sales-order", action = "update")
    @DomainEventAudited
    public ApiResponse<SalesOrderResponse> update(@PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @Operation(summary = "更新销售订单状态")
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "sales-order", action = "audit")
    @DomainEventAudited
    public ApiResponse<SalesOrderResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", service.updateStatus(id, request.status()));
    }

    @Operation(summary = "完成销售")
    @PostMapping("/{id}/complete")
    @RequiresPermission(resource = "sales-order", action = "audit")
    @DomainEventAudited
    public ApiResponse<SalesOrderResponse> complete(@PathVariable Long id) {
        return ApiResponse.success("完成销售成功", service.completeSalesOrder(id));
    }

    @Operation(summary = "删除销售订单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "sales-order", action = "delete")
    @DomainEventAudited
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }

    private ResponseEntity<byte[]> toDownloadResponse(FileDownloadResponse file, HttpServletRequest request) {
        request.setAttribute(OperationLogResultCollector.BUSINESS_NO_ATTRIBUTE, file.businessNo());
        request.setAttribute(OperationLogResultCollector.RECORD_ID_ATTRIBUTE, file.recordId());
        request.setAttribute(OperationLogResultCollector.MODULE_KEY_ATTRIBUTE, file.moduleKey());
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
