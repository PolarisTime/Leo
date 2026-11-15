package com.leo.erp.sales.order.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactAccess;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.web.ExportArtifactResponse;
import com.leo.erp.common.export.web.ExportArtifactResponses;
import com.leo.erp.sales.order.service.SalesOrderXlsxExportArtifactService;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.ResourceVersionPrecondition;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.order.SalesOrderProperties;
import com.leo.erp.sales.order.service.SalesOrderPrintXlsxOptions;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.service.SalesOrderDocumentFlowService;
import com.leo.erp.sales.order.service.SalesOrderSourceCandidateService;
import com.leo.erp.sales.order.web.dto.SalesOrderDocumentFlowResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderPrintXlsxRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderOutboundImportCandidatesCriteria;
import com.leo.erp.sales.order.web.dto.SalesOrderPageCriteria;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateResponse;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.StatusTransitionPermissionGuard;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.api.Created;
import com.leo.erp.common.api.NoContent;

@Tag(name = "销售订单")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/sales-orders")
public class SalesOrderController {

    private final SalesOrderService service;
    private final SalesOrderSourceCandidateService sourceCandidateService;
    private final SalesOrderDocumentFlowService documentFlowService;
    private final SalesOrderXlsxExportArtifactService xlsxExportArtifactService;
    private final ExportArtifactStore xlsxExportArtifactStore;
    private final SalesOrderProperties properties;

    public SalesOrderController(SalesOrderService service,
                                  SalesOrderSourceCandidateService sourceCandidateService,
                                  SalesOrderDocumentFlowService documentFlowService,
                                  SalesOrderXlsxExportArtifactService xlsxExportArtifactService,
                                  ExportArtifactStore xlsxExportArtifactStore,
                                  SalesOrderProperties properties) {
        this.service = service;
        this.sourceCandidateService = sourceCandidateService;
        this.documentFlowService = documentFlowService;
        this.xlsxExportArtifactService = xlsxExportArtifactService;
        this.xlsxExportArtifactStore = xlsxExportArtifactStore;
        this.properties = properties;
    }

    @Operation(summary = "分页查询销售订单采购来源候选")
    @GetMapping("/source-candidates")
    @RequirePermission(PermissionCodes.SALES_ORDERS_READ)
    public PageResponse<SalesOrderSourceCandidateResponse> sourceCandidates(@BindPageQuery(sortFieldKey = "purchase-order") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) Long currentSalesOrderId) {
        return sourceCandidateService.page(
                keyword, supplierId, settlementCompanyId,
                startDate, endDate, currentSalesOrderId, query);
    }

    @Operation(summary = "分页查询销售订单")
    @GetMapping
    @RequirePermission(PermissionCodes.SALES_ORDERS_READ)
    public PageResponse<SalesOrderResponse> page(@BindPageQuery(sortFieldKey = "sales-order") PageQuery query, @ModelAttribute SalesOrderPageCriteria criteria) {
        return PageResponse.from(service.page(
                query,
                PageFilter.of(criteria.getKeyword(), criteria.getCustomerName(), criteria.getProjectName(), criteria.getSettlementCompanyId(), criteria.getStatus(), criteria.getStartDate(), criteria.getEndDate())
                        .withIdentity(criteria.getCustomerId(), criteria.getProjectId(), null, null, null),
                criteria.getProductKeyword(),
                criteria.getPendingOnly()
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
    @RequirePermission(PermissionCodes.SALES_ORDERS_READ)
    public PageResponse<SalesOrderResponse> outboundImportCandidates(@BindPageQuery(sortFieldKey = "sales-order") PageQuery query, @ModelAttribute SalesOrderOutboundImportCandidatesCriteria criteria) {
        return PageResponse.from(service.outboundImportCandidates(
                query,
                PageFilter.of(criteria.getKeyword(), criteria.getCustomerName(), criteria.getProjectName(), criteria.getSettlementCompanyId(), criteria.getStatus(), criteria.getStartDate(), criteria.getEndDate())
                        .withIdentity(criteria.getCustomerId(), criteria.getProjectId(), null, null, criteria.getCurrentRecordId())
        ));
    }

    @Operation(summary = "查询销售订单详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.SALES_ORDERS_READ)
    public SalesOrderResponse detail(@PathVariable @SnowflakeId Long id) {
        return service.detail(id);
    }

    @Operation(summary = "查询销售订单单据流")
    @GetMapping("/{id}/document-flow")
    @RequirePermission(PermissionCodes.SALES_ORDERS_READ)
    public SalesOrderDocumentFlowResponse documentFlow(@PathVariable @SnowflakeId Long id) {
        return documentFlowService.documentFlow(id);
    }

    @Operation(summary = "创建销售订单套打 Excel 导出请求",
            description = """
                    同步生成套打 Excel 导出产物并登记为资源；成功 201 + Location。
                    产物从属于销售订单（parentType=sales-order），请求体可选打印选项；
                    重复提交请携带 Idempotency-Key。产物默认保留 30 分钟，过期后 content 端点返回 404。"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "导出产物已创建（Location 指向本资源）"),
            @ApiResponse(responseCode = "403", description = "缺少 sales-orders:export 权限",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "404", description = "销售订单不存在",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "429", description = "并发导出数达到上限（快速失败，携带 Retry-After）",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @PostMapping("/{id}/xlsx-export-requests")
    @OperationLoggable(moduleName = "销售订单", actionType = "打印", businessNoFields = {"id"}, recordIdField = "id")
    @RequirePermission(PermissionCodes.SALES_ORDERS_EXPORT)
    public ResponseEntity<ExportArtifactResponse> createXlsxExportRequest(
            @PathVariable @SnowflakeId Long id,
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody(required = false) SalesOrderPrintXlsxRequest payload) {
        SalesOrderPrintXlsxOptions options = payload == null
                ? SalesOrderPrintXlsxOptions.defaults()
                : payload.resolvedPrintOptions();
        ExportArtifact artifact = xlsxExportArtifactService.create(
                id, options, principal == null ? null : principal.id());
        String collectionPath = collectionPath(id);
        return ResponseSupport.created(collectionPath, ExportArtifactResponse.of(artifact, collectionPath));
    }

    @Operation(summary = "查询销售订单套打导出资源",
            description = "返回导出产物的元数据（文件名、媒体类型、字节数、过期时间、contentPath）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出资源元数据"),
            @ApiResponse(responseCode = "404", description = "导出结果不存在、已过期或不属于该订单",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}/xlsx-export-requests/{exportId}")
    @RequirePermission(PermissionCodes.SALES_ORDERS_EXPORT)
    public ExportArtifactResponse xlsxExportRequest(@AuthenticationPrincipal SecurityPrincipal principal,
                                                    @PathVariable @SnowflakeId Long id,
                                                    @PathVariable @SnowflakeId Long exportId) {
        return ExportArtifactResponse.of(requireXlsxExportArtifact(principal, id, exportId), collectionPath(id));
    }

    @Operation(summary = "读取销售订单套打导出文件内容",
            description = "文件内容是资源表示：200 + XLSX 媒体类型与 Content-Disposition 附件头。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出文件内容",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "导出结果不存在、已过期或不属于该订单",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}/xlsx-export-requests/{exportId}/content")
    @RequirePermission(PermissionCodes.SALES_ORDERS_EXPORT)
    public ResponseEntity<Resource> xlsxExportRequestContent(@AuthenticationPrincipal SecurityPrincipal principal,
                                                             @PathVariable @SnowflakeId Long id,
                                                             @PathVariable @SnowflakeId Long exportId) {
        return ExportArtifactResponses.content(requireXlsxExportArtifact(principal, id, exportId));
    }

    private ExportArtifact requireXlsxExportArtifact(SecurityPrincipal principal, Long id, Long exportId) {
        ExportArtifact artifact = ExportArtifactAccess.require(
                xlsxExportArtifactStore,
                ExportArtifactKinds.SALES_ORDER_XLSX_EXPORT,
                exportId,
                principal == null ? null : principal.id()
        );
        return ExportArtifactAccess.requireParent(
                artifact, SalesOrderXlsxExportArtifactService.PARENT_TYPE, id);
    }

    private String collectionPath(Long orderId) {
        return "/sales-orders/" + orderId + "/xlsx-export-requests";
    }

    @Operation(summary = "创建销售订单")
    @IdempotencyRequired
    @PostMapping
    @DomainEventAudited
    @Created
    @RequirePermission(PermissionCodes.SALES_ORDERS_CREATE)
    public ResponseEntity<SalesOrderResponse> create(@Valid @RequestBody SalesOrderRequest request) {
        return ResponseSupport.created("/sales-orders", service.create(request));
    }

    @Operation(summary = "更新销售订单",
            description = "整体替换订单。写操作要求资源版本前置条件头 "
                    + ResourceVersionPrecondition.HEADER + "："
                    + "缺少版本返回 428，与单据当前版本不一致返回 412，一致才更新；"
                    + "成功返回 200 + 更新后的订单表示（含新 version）。"
                    + "版本号即列表/详情响应里的 version 字段。")
    @IdempotencyRequired
    @PutMapping("/{id}")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.SALES_ORDERS_UPDATE)
    public SalesOrderResponse update(
            @PathVariable @SnowflakeId Long id,
            @Parameter(description = "资源版本(强比较), 取详情/列表响应的 version 字段")
            @RequestHeader(value = ResourceVersionPrecondition.HEADER, required = false) String resourceVersion,
            @Valid @RequestBody SalesOrderRequest request) {
        return service.update(id, request,
                ResourceVersionPrecondition.parse(resourceVersion, properties.isRequireResourceVersion()));
    }

    /**
     * 交付核定端点：保存订单并确认交付核定，返回更新后的父订单表示。
     * <p>这是状态迁移 + 保存，不是创建资源——不返回 201，也不发 Location；成功一律 200 + 最新订单。</p>
     */
    @Operation(summary = "确认销售订单交付核定（状态迁移，200 + 最新订单）")
    @IdempotencyRequired
    @PostMapping("/{id}/delivery-verifications")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.SALES_ORDERS_CONFIRM)
    public ResponseEntity<SalesOrderResponse> createDeliveryVerification(@PathVariable @SnowflakeId Long id,
                                                                         @Valid @RequestBody SalesOrderRequest request) {
        return ResponseEntity.ok(service.updateAndComplete(id, request));
    }

    @Operation(summary = "更新销售订单状态")
    @IdempotencyRequired
    @PatchMapping("/{id}/status")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.SALES_ORDERS_UPDATE)
    public SalesOrderResponse updateStatus(@PathVariable @SnowflakeId Long id, @Valid @RequestBody StatusUpdateRequest request) {
        // 入口 fail-closed：目标态所需动作权限与 POST /completions、/delivery-verifications 同一迁移表。
        StatusTransitionPermissionGuard.requireForTransitionAtEntry(ModuleKeys.SALES_ORDER, request.status());
        return service.updateStatus(id, request.status());
    }

    /**
     * 完成销售端点：把订单推进到完成态，返回完成后父订单表示。
     * <p>这是状态迁移，不是创建资源——不返回 201，也不发 Location；成功一律 200 + 最新订单。</p>
     */
    @Operation(summary = "完成销售订单（状态迁移，200 + 最新订单）")
    @IdempotencyRequired
    @PostMapping("/{id}/completions")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.SALES_ORDERS_COMPLETE)
    public ResponseEntity<SalesOrderResponse> createCompletion(@PathVariable @SnowflakeId Long id) {
        return ResponseEntity.ok(service.completeSalesOrder(id));
    }

    @Operation(summary = "删除销售订单")
    @DeleteMapping("/{id}")
    @DomainEventAudited
    @NoContent
    @RequirePermission(PermissionCodes.SALES_ORDERS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        service.delete(id);
        return ResponseSupport.noContent();
    }
}
