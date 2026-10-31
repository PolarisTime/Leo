package com.leo.erp.sales.outbound.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.sales.outbound.service.SalesOutboundService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "销售出库")
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/sales-outbounds")
public class V2SalesOutboundController {

    private final SalesOutboundService service;

    public V2SalesOutboundController(SalesOutboundService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询销售出库")
    @RequirePermission(PermissionCodes.SALES_OUTBOUNDS_READ)
    public PageResponse<SalesOutboundResponse> page(@BindPageQuery(sortFieldKey = "sales-outbound") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId, @RequestParam(required = false) String customerName, @RequestParam(required = false) Long projectId, @RequestParam(required = false) String projectName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String productKeyword, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(service.page(
                query,
                PageFilter.of(keyword, customerName, projectName, settlementCompanyId, status, startDate, endDate)
                        .withIdentity(customerId, projectId, null, null, null),
                productKeyword
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询销售出库详情")
    @RequirePermission(PermissionCodes.SALES_OUTBOUNDS_READ)
    public SalesOutboundResponse detail(@PathVariable @SnowflakeId Long id) {
        return service.detail(id);
    }

    @PostMapping
    @Operation(summary = "创建销售出库")
    @DomainEventAudited
    @V2Created
    @RequirePermission(PermissionCodes.SALES_OUTBOUNDS_CREATE)
    public ResponseEntity<SalesOutboundResponse> create(@Valid @RequestBody SalesOutboundRequest request) {
        return V2ResponseSupport.created("/sales-outbounds", service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新销售出库")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.SALES_OUTBOUNDS_UPDATE)
    public SalesOutboundResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody SalesOutboundRequest request) {
        return service.update(id, request);
    }

    /**
     * 审核动作端点：完成审核，返回审核后的父单据表示。
     * <p>携带请求体时先保存再审核（等价于旧 save-and-audit），省略时仅审核既有草稿。</p>
     * <p>这是状态迁移，不是创建资源——不返回 201，也不发 Location（无独立可回读的审核记录资源）；
     * 成功一律 200 + 最新单据。</p>
     */
    @Operation(summary = "审核销售出库（状态迁移，200 + 最新单据）")
    @IdempotencyRequired
    @PostMapping("/{id}/audits")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.SALES_OUTBOUNDS_AUDIT)
    public ResponseEntity<SalesOutboundResponse> createAudit(@PathVariable @SnowflakeId Long id,
                                                             @Valid @RequestBody(required = false) SalesOutboundRequest request) {
        SalesOutboundResponse response = request == null
                ? service.audit(id)
                : service.updateAndAudit(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "更新销售出库状态")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.SALES_OUTBOUNDS_UPDATE)
    public SalesOutboundResponse updateStatus(@PathVariable @SnowflakeId Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除销售出库")
    @DomainEventAudited
    @V2NoContent
    @RequirePermission(PermissionCodes.SALES_OUTBOUNDS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        service.delete(id);
        return V2ResponseSupport.noContent();
    }
}
