package com.leo.erp.purchase.inbound.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "采购入库")
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/purchase-inbounds")
public class V2PurchaseInboundController {

    private final PurchaseInboundService service;

    public V2PurchaseInboundController(PurchaseInboundService service) {
        this.service = service;
    }

    /**
     * 分页查询采购入库。
     *
     * <p>{@code productKeyword} 是「商品信息」筛选（品牌/材质/规格/长度），与单据头关键字
     * {@code keyword} 相互独立：前者穿透到明细行，后者只匹配表头字段。参数名与销售出库
     * 保持一致，前端可用同一套筛选控件。</p>
     */
    @GetMapping
    @Operation(summary = "分页查询采购入库")
    @RequirePermission(PermissionCodes.PURCHASE_INBOUNDS_READ)
    public PageResponse<PurchaseInboundResponse> page(@BindPageQuery(sortFieldKey = "purchase-inbound") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) String supplierName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String productKeyword, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(service.page(
                query,
                PageFilter.of(keyword, supplierName, settlementCompanyId, status, startDate, endDate)
                        .withIdentity(null, null, supplierId, null, null),
                productKeyword
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询采购入库详情")
    @RequirePermission(PermissionCodes.PURCHASE_INBOUNDS_READ)
    public PurchaseInboundResponse detail(@PathVariable @SnowflakeId Long id) {
        return service.detail(id);
    }

    @PostMapping
    @Operation(summary = "创建采购入库")
    @DomainEventAudited
    @V2Created
    @RequirePermission(PermissionCodes.PURCHASE_INBOUNDS_CREATE)
    public ResponseEntity<PurchaseInboundResponse> create(@Valid @RequestBody PurchaseInboundRequest request) {
        return V2ResponseSupport.created("/purchase-inbounds", service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新采购入库")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.PURCHASE_INBOUNDS_UPDATE)
    public PurchaseInboundResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody PurchaseInboundRequest request) {
        return service.update(id, request);
    }

    /**
     * 审核动作端点：完成审核，返回审核后的父单据表示。
     * <p>携带请求体时先保存再审核（等价于旧 save-and-audit），省略时仅审核既有草稿。</p>
     * <p>这是状态迁移，不是创建资源——不返回 201，也不发 Location（无独立可回读的审核记录资源）；
     * 成功一律 200 + 最新单据。</p>
     */
    @Operation(summary = "审核采购入库（状态迁移，200 + 最新单据）")
    @IdempotencyRequired
    @PostMapping("/{id}/audits")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.PURCHASE_INBOUNDS_AUDIT)
    public ResponseEntity<PurchaseInboundResponse> createAudit(@PathVariable @SnowflakeId Long id,
                                                               @Valid @RequestBody(required = false) PurchaseInboundRequest request) {
        PurchaseInboundResponse response = request == null
                ? service.audit(id)
                : service.updateAndAudit(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "更新采购入库状态")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.PURCHASE_INBOUNDS_UPDATE)
    public PurchaseInboundResponse updateStatus(@PathVariable @SnowflakeId Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除采购入库")
    @DomainEventAudited
    @V2NoContent
    @RequirePermission(PermissionCodes.PURCHASE_INBOUNDS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        service.delete(id);
        return V2ResponseSupport.noContent();
    }
}
