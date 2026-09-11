package com.leo.erp.sales.order.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.sales.order.service.SalesOrderPrintExportService;
import com.leo.erp.sales.order.service.SalesOrderPrintXlsxOptions;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.service.SalesOrderSourceCandidateService;
import com.leo.erp.system.operationlog.support.OperationLogResultCollector;
import com.leo.erp.sales.order.web.dto.SalesOrderPrintXlsxRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderOutboundImportCandidatesCriteria;
import com.leo.erp.sales.order.web.dto.SalesOrderPageCriteria;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;

@Tag(name = "销售订单")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/sales-orders")
public class V2SalesOrderController {

    private final SalesOrderService service;
    private final SalesOrderPrintExportService printExportService;
    private final SalesOrderSourceCandidateService sourceCandidateService;

    public V2SalesOrderController(SalesOrderService service,
                                  SalesOrderPrintExportService printExportService,
                                  SalesOrderSourceCandidateService sourceCandidateService) {
        this.service = service;
        this.printExportService = printExportService;
        this.sourceCandidateService = sourceCandidateService;
    }

    @Operation(summary = "分页查询销售订单采购来源候选")
    @GetMapping("/source-candidates")
    public PageResponse<SalesOrderSourceCandidateResponse> sourceCandidates(@BindPageQuery(sortFieldKey = "purchase-order") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) Long currentSalesOrderId) {
        return sourceCandidateService.page(
                keyword, supplierId, settlementCompanyId,
                startDate, endDate, currentSalesOrderId, query);
    }

    @Operation(summary = "分页查询销售订单")
    @GetMapping
    public PageResponse<SalesOrderResponse> page(@BindPageQuery(sortFieldKey = "sales-order") PageQuery query, @ModelAttribute SalesOrderPageCriteria criteria) {
        return PageResponse.from(service.page(
                query,
                PageFilter.of(criteria.getKeyword(), criteria.getCustomerName(), criteria.getProjectName(), criteria.getSettlementCompanyId(), criteria.getStatus(), criteria.getStartDate(), criteria.getEndDate())
                        .withIdentity(criteria.getCustomerId(), criteria.getProjectId(), null, null, null),
                criteria.getProductKeyword(),
                criteria.getPendingOnly(),
                criteria.getReferenced(),
                criteria.getReferencedBy()
        ));
    }

    /** 兼容旧调用方，默认查询全部销售订单。 */
    public PageResponse<SalesOrderResponse> page(PageQuery query, String keyword, Long customerId,
                                                  String customerName, Long projectId, String projectName,
                                                  Long settlementCompanyId, String productKeyword, String status,
                                                  LocalDate startDate, LocalDate endDate) {
        return PageResponse.from(service.page(
                query,
                PageFilter.of(keyword, customerName, projectName, settlementCompanyId, status, startDate, endDate)
                        .withIdentity(customerId, projectId, null, null, null),
                productKeyword
        ));
    }

    @Operation(summary = "分页查询销售订单出库导入候选")
    @GetMapping("/outbound-import-candidates")
    public PageResponse<SalesOrderResponse> outboundImportCandidates(@BindPageQuery(sortFieldKey = "sales-order") PageQuery query, @ModelAttribute SalesOrderOutboundImportCandidatesCriteria criteria) {
        return PageResponse.from(service.outboundImportCandidates(
                query,
                PageFilter.of(criteria.getKeyword(), criteria.getCustomerName(), criteria.getProjectName(), criteria.getSettlementCompanyId(), criteria.getStatus(), criteria.getStartDate(), criteria.getEndDate())
                        .withIdentity(criteria.getCustomerId(), criteria.getProjectId(), null, null, criteria.getCurrentRecordId())
        ));
    }

    @Operation(summary = "查询销售订单详情")
    @GetMapping("/{id}")
    public SalesOrderResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @Operation(summary = "创建销售订单套打 Excel 导出（同步返回文件）")
    @PostMapping("/{id}/xlsx-exports")
    @OperationLoggable(moduleName = "销售订单", actionType = "打印", businessNoFields = {"id"}, recordIdField = "id")
    public ResponseEntity<byte[]> createXlsxExport(@PathVariable Long id, @Valid @RequestBody(required = false) SalesOrderPrintXlsxRequest payload, HttpServletRequest request) {
        SalesOrderPrintXlsxOptions options = payload == null
                ? SalesOrderPrintXlsxOptions.defaults()
                : payload.resolvedPrintOptions();
        return toDownloadResponse(printExportService.exportSalesOrderPrint(id, options), request);
    }

    @Operation(summary = "创建销售订单")
    @IdempotencyRequired
    @PostMapping
    @DomainEventAudited
    @V2Created
    public ResponseEntity<SalesOrderResponse> create(@Valid @RequestBody SalesOrderRequest request) {
        return V2ResponseSupport.created("/sales-orders", service.create(request));
    }

    @Operation(summary = "更新销售订单")
    @IdempotencyRequired
    @PutMapping("/{id}")
    @DomainEventAudited
    public SalesOrderResponse update(@PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) {
        return service.update(id, request);
    }

    /**
     * 资源型交付核定端点：创建“交付核定”子资源即保存订单并确认交付核定。
     * 子资源无独立可回读路径，Location 指向父订单 {@code GET /api/v2.0/sales-orders/{id}}。
     */
    @Operation(summary = "创建销售订单交付核定（保存并确认交付核定）")
    @IdempotencyRequired
    @PostMapping("/{id}/delivery-verifications")
    @DomainEventAudited
    @V2Created
    public ResponseEntity<SalesOrderResponse> createDeliveryVerification(@PathVariable Long id,
                                                                         @Valid @RequestBody SalesOrderRequest request) {
        return V2ResponseSupport.created("/sales-orders", service.updateAndComplete(id, request));
    }

    @Operation(summary = "更新销售订单状态")
    @IdempotencyRequired
    @PatchMapping("/{id}/status")
    @DomainEventAudited
    public SalesOrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(id, request.status());
    }

    /**
     * 资源型完成销售端点：创建“完成记录”子资源即完成销售。
     * 子资源无独立可回读路径，Location 指向父订单 {@code GET /api/v2.0/sales-orders/{id}}。
     */
    @Operation(summary = "创建销售订单完成记录（完成销售）")
    @IdempotencyRequired
    @PostMapping("/{id}/completions")
    @DomainEventAudited
    @V2Created
    public ResponseEntity<SalesOrderResponse> createCompletion(@PathVariable Long id) {
        return V2ResponseSupport.created("/sales-orders", service.completeSalesOrder(id));
    }

    @Operation(summary = "删除销售订单")
    @DeleteMapping("/{id}")
    @DomainEventAudited
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return V2ResponseSupport.noContent();
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
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(file.content());
    }
}
