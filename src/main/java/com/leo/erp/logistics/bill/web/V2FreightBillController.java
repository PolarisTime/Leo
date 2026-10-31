package com.leo.erp.logistics.bill.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.logistics.bill.service.FreightBillSalesOrderCandidateService;
import com.leo.erp.logistics.bill.service.FreightBillService;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.StatusTransitionPermissionGuard;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import com.leo.erp.logistics.bill.web.dto.FreightBillSalesOrderCandidateResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/freight-bills")
public class V2FreightBillController {

    private final FreightBillService service;
    private final FreightBillSalesOrderCandidateService candidateService;
    private final FreightBillSalesOrderCandidateResponseAssembler candidateResponseAssembler;

    public V2FreightBillController(FreightBillService service,
                                   FreightBillSalesOrderCandidateService candidateService,
                                   FreightBillSalesOrderCandidateResponseAssembler candidateResponseAssembler) {
        this.service = service;
        this.candidateService = candidateService;
        this.candidateResponseAssembler = candidateResponseAssembler;
    }

    @GetMapping("/sales-order-candidates")
    @Operation(summary = "分页查询物流单的销售订单来源候选")
    @RequirePermission(PermissionCodes.FREIGHT_BILLS_READ)
    public PageResponse<FreightBillSalesOrderCandidateResponse> salesOrderCandidates(@BindPageQuery(sortFieldKey = "sales-order") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId, @RequestParam(required = false) String customerName, @RequestParam(required = false) Long projectId, @RequestParam(required = false) String projectName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) Long currentRecordId) {
        return candidateResponseAssembler.toPageResponse(candidateService.page(
                query,
                PageFilter.of(
                                keyword,
                                customerName,
                                projectName,
                                settlementCompanyId,
                                // 空白 = 前端已 opt-out 该筛选项 → null(不额外过滤); 池外状态 → 422
                                StatusConstants.normalizeOptionalSalesOrderCandidateStatus(status, "status"),
                                startDate,
                                endDate
                        )
                        .withIdentity(customerId, projectId, null, null, currentRecordId)
        ));
    }

    @GetMapping
    @Operation(summary = "分页查询物流单")
    @RequirePermission(PermissionCodes.FREIGHT_BILLS_READ)
    public PageResponse<FreightBillResponse> page(@BindPageQuery(sortFieldKey = "freight-bill") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long carrierId, @RequestParam(required = false) String carrierCode, @RequestParam(required = false) String carrierName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String productKeyword, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(service.page(
                query,
                PageFilter.of(keyword, carrierName, settlementCompanyId, status, startDate, endDate)
                        .withIdentity(null, null, null, carrierId, null),
                carrierCode,
                productKeyword
        ));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.FREIGHT_BILLS_READ)
    public FreightBillResponse detail(@PathVariable @SnowflakeId Long id) {
        return service.detail(id);
    }

    @PostMapping
    @DomainEventAudited
    @V2Created
    @RequirePermission(PermissionCodes.FREIGHT_BILLS_CREATE)
    public ResponseEntity<FreightBillResponse> create(@Valid @RequestBody FreightBillRequest request) {
        return V2ResponseSupport.created("/freight-bills", service.create(request));
    }

    @PutMapping("/{id}")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.FREIGHT_BILLS_UPDATE)
    public FreightBillResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody FreightBillRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.FREIGHT_BILLS_UPDATE)
    public FreightBillResponse updateStatus(@PathVariable @SnowflakeId Long id, @Valid @RequestBody StatusUpdateRequest request) {
        // 入口 fail-closed：无已认证主体直接 403；目标态需要的动作权限与 POST /audits 同一迁移表。
        StatusTransitionPermissionGuard.requireForTransitionAtEntry(ModuleKeys.FREIGHT_BILL, request.status());
        return service.updateStatus(id, request.status());
    }

    /**
     * 审核动作端点：把物流单推进到已审核，返回推进后的父资源表示。
     * <p>这是状态迁移，不是创建资源——不返回 201，也不发 Location（无独立可回读的审核记录资源）；
     * 成功一律 200 + 最新物流单。反审核仍使用 {@code PATCH /freight-bills/{id}/status}。</p>
     */
    @Operation(summary = "审核物流单（状态迁移，200 + 最新单据）")
    @IdempotencyRequired
    @PostMapping("/{id}/audits")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.FREIGHT_BILLS_AUDIT)
    public ResponseEntity<FreightBillResponse> createAudit(@PathVariable @SnowflakeId Long id) {
        return ResponseEntity.ok(service.updateStatus(id, StatusConstants.AUDITED));
    }

    @DeleteMapping("/{id}")
    @DomainEventAudited
    @V2NoContent
    @RequirePermission(PermissionCodes.FREIGHT_BILLS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        service.delete(id);
        return V2ResponseSupport.noContent();
    }
}
