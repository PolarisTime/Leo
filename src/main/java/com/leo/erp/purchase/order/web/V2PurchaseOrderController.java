package com.leo.erp.purchase.order.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.support.OptionLimits;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.order.service.PurchaseOrderForceCloseService;
import com.leo.erp.purchase.order.service.PurchaseOrderPickupListService;
import com.leo.erp.purchase.order.service.PurchaseOrderOptionService;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.purchase.order.service.PurchaseOrderWarehouseRecommendationService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderForceCloseRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderOptionResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderPageCriteria;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderPickupListResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderWarehouseRecommendationResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@Tag(name = "采购订单")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/purchase-orders")
public class V2PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderPickupListService pickupListService;
    private final PurchaseOrderWarehouseRecommendationService warehouseRecommendationService;
    private final PurchaseOrderOptionService purchaseOrderOptionService;
    private final PurchaseOrderForceCloseService purchaseOrderForceCloseService;

    public V2PurchaseOrderController(PurchaseOrderService purchaseOrderService,
                                     PurchaseOrderPickupListService pickupListService,
                                     PurchaseOrderWarehouseRecommendationService warehouseRecommendationService,
                                     PurchaseOrderOptionService purchaseOrderOptionService,
                                     PurchaseOrderForceCloseService purchaseOrderForceCloseService) {
        this.purchaseOrderService = purchaseOrderService;
        this.pickupListService = pickupListService;
        this.warehouseRecommendationService = warehouseRecommendationService;
        this.purchaseOrderOptionService = purchaseOrderOptionService;
        this.purchaseOrderForceCloseService = purchaseOrderForceCloseService;
    }

    @Operation(summary = "采购订单下拉选项(单号/供应商/订货吨数/状态)")
    @GetMapping("/options")
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_READ)
    public List<PurchaseOrderOptionResponse> options(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String status) {
        return OptionLimits.cap(purchaseOrderOptionService.listOptions(keyword, status));
    }

    @Operation(summary = "分页查询采购入库来源候选")
    @GetMapping("/inbound-import-candidates")
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_READ)
    public PageResponse<PurchaseOrderImportCandidateResponse> inboundImportCandidates(@BindPageQuery(sortFieldKey = "purchase-order") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) String supplierName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) Long currentRecordId) {
        return PageResponse.from(purchaseOrderService.inboundImportCandidates(
                query,
                PageFilter.of(keyword, supplierName, settlementCompanyId, status, startDate, endDate)
                        .withIdentity(null, null, supplierId, null, currentRecordId)
        ));
    }

    @Operation(summary = "分页查询采购订单")
    @GetMapping
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_READ)
    public PageResponse<PurchaseOrderResponse> page(@BindPageQuery(sortFieldKey = "purchase-order") PageQuery query, @ModelAttribute PurchaseOrderPageCriteria criteria) {
        return PageResponse.from(purchaseOrderService.page(
                query,
                PageFilter.of(criteria.getKeyword(), criteria.getSupplierName(), criteria.getSettlementCompanyId(), criteria.getStatus(), criteria.getStartDate(), criteria.getEndDate())
                        .withIdentity(null, null, criteria.getSupplierId(), null, null),
                criteria.getPendingOnly(),
                criteria.getReferenced(),
                criteria.getReferencedBy()
        ));
    }

    @Operation(summary = "按供应商和商品推荐采购仓库")
    @GetMapping("/warehouse-recommendations")
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_READ)
    public List<PurchaseOrderWarehouseRecommendationResponse> warehouseRecommendations(@RequestParam @Positive Long supplierId, @RequestParam @Size(min = 1, max = 200) List<@Positive Long> materialIds) {
        return warehouseRecommendationService.recommend(supplierId, materialIds).stream()
                .map(PurchaseOrderWarehouseRecommendationResponse::from)
                .toList();
    }

    @Operation(summary = "预览采购订单提货清单")
    @GetMapping("/pickup-list-preview")
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_READ)
    public PurchaseOrderPickupListResponse pickupListPreview(@RequestParam @Size(min = 1, max = 50) List<@Positive Long> orderIds) {
        return pickupListService.preview(orderIds);
    }

    @Operation(summary = "查询采购订单详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_READ)
    public PurchaseOrderResponse detail(@PathVariable Long id) {
        return purchaseOrderService.detail(id);
    }

    @Operation(summary = "创建采购订单")
    @PostMapping
    @DomainEventAudited
    @V2Created
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_CREATE)
    public ResponseEntity<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderRequest request) {
        return V2ResponseSupport.created("/purchase-orders", purchaseOrderService.create(request));
    }

    @Operation(summary = "更新采购订单")
    @PutMapping("/{id}")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_UPDATE)
    public PurchaseOrderResponse update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.update(id, request);
    }

    @Operation(summary = "更新采购订单状态")
    @PatchMapping("/{id}/status")
    @DomainEventAudited
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_UPDATE)
    public PurchaseOrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return purchaseOrderService.updateStatus(id, request.status());
    }

    /**
     * 资源型强制结单端点：创建"强制结单记录"子资源即完成强制结单。
     * 子资源无独立可回读路径(DELETE 作用于集合本身)，Location 指向父订单
     * {@code GET /api/v2.0/purchase-orders/{id}}。
     */
    @Operation(
            summary = "强制结单(剩余未入库件数作废)",
            description = "把「已审核」的采购订单剩余未入库件数一次性作废, 并置为「完成采购」; 原因必填并留痕。"
                    + "已强制结单、无未入库件数、存在未审核采购入库单、或剩余件数已被销售直接占用时返回 422。"
    )
    @PostMapping("/{id}/force-closures")
    @DomainEventAudited
    @V2Created
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_FORCE_CLOSE)
    public ResponseEntity<PurchaseOrderResponse> forceClose(@PathVariable Long id,
                                                           @Valid @RequestBody PurchaseOrderForceCloseRequest request,
                                                           @AuthenticationPrincipal SecurityPrincipal principal) {
        PurchaseOrderResponse response = purchaseOrderForceCloseService.forceClose(id, request.reason(), principal);
        return V2ResponseSupport.created("/purchase-orders", response);
    }

    @Operation(
            summary = "撤销强制结单",
            description = "把强制结单的采购订单退回「已审核」并清空留痕, 未入库件数重新可入库; "
                    + "供应商台账锁为一次性闩锁, 与采购入库反审核回退一致, 不随撤销解锁。"
    )
    @DeleteMapping("/{id}/force-closures")
    @DomainEventAudited
    @V2NoContent
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_FORCE_CLOSE)
    public ResponseEntity<Void> cancelForceClose(@PathVariable Long id,
                                                @AuthenticationPrincipal SecurityPrincipal principal) {
        purchaseOrderForceCloseService.cancelForceClose(id, principal);
        return V2ResponseSupport.noContent();
    }

    @Operation(summary = "删除采购订单")
    @DeleteMapping("/{id}")
    @DomainEventAudited
    @V2NoContent
    @RequirePermission(PermissionCodes.PURCHASE_ORDERS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        purchaseOrderService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
